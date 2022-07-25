package com.phonepe.drove.controller.statedb;

import com.phonepe.drove.models.taskinstance.TaskInstanceInfo;
import com.phonepe.drove.models.taskinstance.TaskInstanceState;
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

    private final ConsumingFireForgetSignal<TaskInstanceInfo> stateChanged = new ConsumingFireForgetSignal<>();

    public final ConsumingFireForgetSignal<TaskInstanceInfo> onStateChange() {
        return stateChanged;
    }

    public abstract Map<String, List<TaskInstanceInfo>> tasks(
            Collection<String> sourceAppIds, Set<TaskInstanceState> validStates, boolean skipStaleCheck);

    public abstract Optional<TaskInstanceInfo> task(String sourceAppName, String taskId);

    public final boolean updateTask(String sourceAppName, String taskId, TaskInstanceInfo taskInstanceInfo) {
        val oldTask = task(sourceAppName, taskId).orElse(null);
        val status = updateTaskImpl(sourceAppName, taskId, taskInstanceInfo);
        if (status) {
            val oldState = null != oldTask ? oldTask.getState() : null;
            if (null != taskInstanceInfo) {
                if (oldState != taskInstanceInfo.getState()) {
                    log.info("Task {}/{} changed state from: {} to: {}",
                             sourceAppName, taskId, oldState, taskInstanceInfo.getState());
                    stateChanged.dispatch(taskInstanceInfo);
                }
            }
        }
        return status;
    }

    protected abstract boolean updateTaskImpl(String sourceAppName, String taskId, TaskInstanceInfo taskInstanceInfo);

    public abstract boolean deleteTask(String sourceAppName, String taskId);

    public abstract Optional<TaskInstanceInfo> checkedCurrentState(final String sourceAppName, String taskId);
}
