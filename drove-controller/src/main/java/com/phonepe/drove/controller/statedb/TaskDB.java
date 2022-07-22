package com.phonepe.drove.controller.statedb;

import com.phonepe.drove.models.taskinstance.TaskInstanceInfo;
import com.phonepe.drove.models.taskinstance.TaskInstanceState;

import java.time.Duration;
import java.util.*;

/**
 *
 */
public interface TaskDB {
    Duration MAX_ACCEPTABLE_UPDATE_INTERVAL = Duration.ofMinutes(1);

    Map<String, List<TaskInstanceInfo>> tasks(
            Collection<String> sourceAppIds, Set<TaskInstanceState> validStates, boolean skipStaleCheck);

    Optional<TaskInstanceInfo> task(String sourceAppName, String taskId);

    boolean updateTask(String sourceAppName, String taskId, TaskInstanceInfo taskInstanceInfo);

    boolean deleteTask(String sourceAppName, String taskId);

    long markStaleTask(final String sourceAppName);
}
