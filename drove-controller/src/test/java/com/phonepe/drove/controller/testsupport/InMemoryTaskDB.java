package com.phonepe.drove.controller.testsupport;

import com.phonepe.drove.controller.statedb.TaskDB;
import com.phonepe.drove.models.taskinstance.TaskInstanceInfo;
import com.phonepe.drove.models.taskinstance.TaskInstanceState;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

import javax.inject.Singleton;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import static com.phonepe.drove.models.taskinstance.TaskInstanceState.ACTIVE_STATES;

/**
 *
 */
@Singleton
@Slf4j
public class InMemoryTaskDB extends TaskDB {
    private final Map<String, Map<String, TaskInstanceInfo>> instances = new ConcurrentHashMap<>();

    @Override
    public Map<String, List<TaskInstanceInfo>> tasks(
            Collection<String> sourceAppIds,
            Set<TaskInstanceState> validStates,
            boolean skipStaleCheck) {
        val validUpdateDate = new Date(System.currentTimeMillis() - MAX_ACCEPTABLE_UPDATE_INTERVAL.toMillis());
        return sourceAppIds.stream()
                .map(instances::get)
                .filter(Objects::nonNull)
                .flatMap(instances -> instances.values().stream())
                .filter(instanceInfo -> validStates.contains(instanceInfo.getState()))
                .filter(instanceInfo -> skipStaleCheck || instanceInfo.getUpdated().after(validUpdateDate))
                .collect(Collectors.groupingBy(TaskInstanceInfo::getSourceAppName, Collectors.toUnmodifiableList()));
    }

    @Override
    public Optional<TaskInstanceInfo> task(String sourceAppName, String taskId) {
        return Optional.ofNullable(instances.getOrDefault(sourceAppName, Collections.emptyMap()).get(taskId));
    }

    @Override
    protected boolean updateTaskImpl(String sourceAppName, String taskId, TaskInstanceInfo taskInstanceInfo) {
        instances.compute(sourceAppName, (aId, old) -> {
            val ins = Objects.requireNonNullElse(old, new ConcurrentHashMap<String, TaskInstanceInfo>());
            ins.put(taskId, taskInstanceInfo);
            return ins;
        });
        return true;
    }

    @Override
    public boolean deleteTask(String sourceAppName, String taskId) {
        return !instances.containsKey(sourceAppName) || instances.get(sourceAppName).remove(taskId) != null;
    }

    @Override
    public Optional<TaskInstanceInfo> checkedCurrentState(String sourceAppName, String taskId) {
        val validUpdateDate = new Date(new Date().getTime() - MAX_ACCEPTABLE_UPDATE_INTERVAL.toMillis());
        val instance = task(sourceAppName, taskId).orElse(null);
        if(null == instance
                || !ACTIVE_STATES.contains(instance.getState())
                || instance.getUpdated().after(validUpdateDate)) {
            return Optional.ofNullable(instance);
        }
        log.warn("Found stale task instance {}/{}. Current state: {} Last updated at: {}",
                 sourceAppName, instance.getTaskId(), instance.getState(), instance.getUpdated());
        val updateStatus = updateTaskImpl(sourceAppName,
                                          taskId,
                                          new TaskInstanceInfo(instance.getSourceAppName(),
                                                           instance.getTaskId(),
                                                           instance.getInstanceId(),
                                                           instance.getExecutorId(),
                                                           instance.getHostname(),
                                                           instance.getExecutable(),
                                                           instance.getResources(),
                                                           instance.getVolumes(),
                                                           instance.getLoggingSpec(),
                                                           instance.getEnv(),
                                                           TaskInstanceState.LOST,
                                                           instance.getMetadata(),
                                                           "Instance lost",
                                                           instance.getCreated(),
                                                           new Date()));
        log.info("Stale mark status for task {}/{} is {}", sourceAppName, taskId, updateStatus);
        return task(sourceAppName, taskId);
    }
}
