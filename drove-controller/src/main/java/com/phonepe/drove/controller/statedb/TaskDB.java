/*
 *  Copyright (c) 2024 Original Author(s), PhonePe India Pvt. Ltd.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package com.phonepe.drove.controller.statedb;

import com.phonepe.drove.models.taskinstance.TaskInfo;
import com.phonepe.drove.models.taskinstance.TaskState;
import io.appform.signals.signals.ConsumingFireForgetSignal;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

import java.time.Duration;
import java.util.*;
import java.util.function.Predicate;

/**
 *
 */
@Slf4j
public abstract class TaskDB {
    public static final Duration MAX_ACCEPTABLE_UPDATE_INTERVAL = Duration.ofMinutes(1);

    private final ConsumingFireForgetSignal<TaskInfo> stateChanged = new ConsumingFireForgetSignal<>();

    public final ConsumingFireForgetSignal<TaskInfo> onStateChange() {
        return stateChanged;
    }

    public abstract Map<String, List<TaskInfo>> tasks(
            Collection<String> sourceAppIds, Set<TaskState> validStates, boolean skipStaleCheck);

    public List<TaskInfo> tasks(Map<String, Set<String>> tasks, Set<TaskState> states) {
        return tasks(tasks.keySet(), states, true)
                .values()
                .stream()
                .flatMap(List::stream)
                .filter(task -> tasks.getOrDefault(task.getSourceAppName(), Set.of()).contains(task.getTaskId()))
                .toList();
    }

    public abstract void cleanupTasks(Predicate<TaskInfo> handler);


    public abstract Optional<TaskInfo> task(String sourceAppName, String taskId);

    public final boolean updateTask(String sourceAppName, String taskId, TaskInfo taskInfo) {
        val oldTask = task(sourceAppName, taskId).orElse(null);
        val status = updateTaskImpl(sourceAppName, taskId, taskInfo);
        if (status) {
            val oldState = null != oldTask ? oldTask.getState() : null;
            if (null != taskInfo && oldState != taskInfo.getState()) {
                log.info("Task {}/{} changed state from: {} to: {}",
                             sourceAppName, taskId, oldState, taskInfo.getState());
                stateChanged.dispatch(taskInfo);
            }
        }
        return status;
    }

    protected abstract boolean updateTaskImpl(String sourceAppName, String taskId, TaskInfo taskInfo);

    public abstract boolean deleteTask(String sourceAppName, String taskId);

}
