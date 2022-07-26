package com.phonepe.drove.controller.statedb;

import com.phonepe.drove.models.taskinstance.TaskInfo;
import com.phonepe.drove.models.taskinstance.TaskState;
import io.appform.signals.signals.ConsumingFireForgetSignal;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

import java.time.Duration;
import java.util.*;

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

    public abstract Optional<TaskInfo> task(String sourceAppName, String taskId);

    public final boolean updateTask(String sourceAppName, String taskId, TaskInfo taskInfo) {
        val oldTask = task(sourceAppName, taskId).orElse(null);
        val status = updateTaskImpl(sourceAppName, taskId, taskInfo);
        if (status) {
            val oldState = null != oldTask ? oldTask.getState() : null;
            if (null != taskInfo) {
                if (oldState != taskInfo.getState()) {
                    log.info("Task {}/{} changed state from: {} to: {}",
                             sourceAppName, taskId, oldState, taskInfo.getState());
                    stateChanged.dispatch(taskInfo);
                }
            }
        }
        return status;
    }

    protected abstract boolean updateTaskImpl(String sourceAppName, String taskId, TaskInfo taskInfo);

    public abstract boolean deleteTask(String sourceAppName, String taskId);

    public abstract Optional<TaskInfo> checkedCurrentState(final String sourceAppName, String taskId);
}
