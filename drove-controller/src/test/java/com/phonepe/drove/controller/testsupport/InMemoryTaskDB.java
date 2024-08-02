/*
 *  Copyright (c) 2022 Original Author(s), PhonePe India Pvt. Ltd.
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

package com.phonepe.drove.controller.testsupport;

import com.phonepe.drove.controller.statedb.TaskDB;
import com.phonepe.drove.models.taskinstance.TaskInfo;
import com.phonepe.drove.models.taskinstance.TaskState;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

import javax.inject.Singleton;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 *
 */
@Singleton
@Slf4j
public class InMemoryTaskDB extends TaskDB {
    private final Map<String, Map<String, TaskInfo>> instances = new ConcurrentHashMap<>();

    @Override
    public Map<String, List<TaskInfo>> tasks(
            Collection<String> sourceAppIds,
            Set<TaskState> validStates,
            boolean skipStaleCheck) {
        val validUpdateDate = new Date(System.currentTimeMillis() - MAX_ACCEPTABLE_UPDATE_INTERVAL.toMillis());
        return sourceAppIds.stream()
                .map(instances::get)
                .filter(Objects::nonNull)
                .flatMap(instances -> instances.values().stream())
                .filter(instanceInfo -> validStates.contains(instanceInfo.getState()))
                .filter(instanceInfo -> skipStaleCheck || instanceInfo.getUpdated().after(validUpdateDate))
                .collect(Collectors.groupingBy(TaskInfo::getSourceAppName, Collectors.toUnmodifiableList()));
    }

    @Override
    public void cleanupTasks(Predicate<TaskInfo> handler) {
        instances.forEach((appName, tasks) -> tasks.forEach((taskId, task) -> {
            if(handler.test(task)) {
                log.debug("Removed task info {}/{} from root", appName, taskId);
                instances.get(appName).remove(taskId);
            }
        }));
    }

    @Override
    public Optional<TaskInfo> task(String sourceAppName, String taskId) {
        return Optional.ofNullable(instances.getOrDefault(sourceAppName, Collections.emptyMap()).get(taskId));
    }

    @Override
    protected boolean updateTaskImpl(String sourceAppName, String taskId, TaskInfo taskInfo) {
        instances.compute(sourceAppName, (aId, old) -> {
            val ins = Objects.requireNonNullElse(old, new ConcurrentHashMap<String, TaskInfo>());
            ins.put(taskId, taskInfo);
            return ins;
        });
        return true;
    }

    @Override
    public boolean deleteTask(String sourceAppName, String taskId) {
        return !instances.containsKey(sourceAppName) || instances.get(sourceAppName).remove(taskId) != null;
    }

}
