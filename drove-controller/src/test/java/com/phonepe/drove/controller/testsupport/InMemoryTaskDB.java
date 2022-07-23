package com.phonepe.drove.controller.testsupport;

import com.phonepe.drove.controller.statedb.TaskDB;
import com.phonepe.drove.models.taskinstance.TaskInstanceInfo;
import com.phonepe.drove.models.taskinstance.TaskInstanceState;
import lombok.val;

import javax.inject.Singleton;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 *
 */
@Singleton
public class InMemoryTaskDB implements TaskDB {
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
    public boolean updateTask(String sourceAppName, String taskId, TaskInstanceInfo taskInstanceInfo) {
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
    public long markStaleTask(String sourceAppName) {
        val validUpdateDate = new Date(new Date().getTime() - MAX_ACCEPTABLE_UPDATE_INTERVAL.toMillis());
        //Find all instances in active states that have not been updated in stipulated time and move them to unknown
        // state
        val tasks = tasks(Set.of(sourceAppName), TaskInstanceState.ACTIVE_STATES, true)
                .getOrDefault(sourceAppName, List.of())
                .stream().filter(i -> i.getUpdated().before(validUpdateDate))
                .toList();
        tasks.forEach(instanceInfo -> {
            updateTask(sourceAppName,
                       instanceInfo.getInstanceId(),
                       new TaskInstanceInfo(instanceInfo.getSourceAppName(),
                                            instanceInfo.getTaskId(),
                                            instanceInfo.getInstanceId(),
                                            instanceInfo.getExecutorId(),
                                            instanceInfo.getHostname(),
                                            instanceInfo.getExecutable(),
                                            instanceInfo.getResources(),
                                            instanceInfo.getVolumes(),
                                            instanceInfo.getLoggingSpec(),
                                            instanceInfo.getEnv(),
                                            TaskInstanceState.LOST,
                                            instanceInfo.getMetadata(),
                                            "Instance lost",
                                            instanceInfo.getCreated(),
                                            new Date()));
        });
        return tasks.size();
    }
}
