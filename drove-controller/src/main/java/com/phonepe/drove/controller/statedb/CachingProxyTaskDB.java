package com.phonepe.drove.controller.statedb;

import com.google.common.collect.Sets;
import com.phonepe.drove.controller.managed.LeadershipEnsurer;
import com.phonepe.drove.models.taskinstance.TaskInstanceInfo;
import com.phonepe.drove.models.taskinstance.TaskInstanceState;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import java.util.*;
import java.util.concurrent.locks.StampedLock;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.phonepe.drove.models.taskinstance.TaskInstanceState.ACTIVE_STATES;

/**
 *
 */
@Slf4j
@Singleton
public class CachingProxyTaskDB extends TaskDB {
    private final TaskDB root;

    private final Map<String, Map<String, TaskInstanceInfo>> cache = new HashMap<>();
    private final StampedLock lock = new StampedLock();

    @Inject
    public CachingProxyTaskDB(
            @Named("StoredTaskDB") TaskDB root,
            final LeadershipEnsurer leadershipEnsurer) {
        this.root = root;
        leadershipEnsurer.onLeadershipStateChanged().connect(this::purge);
        log.info("Created object");
    }

    @Override
    public Map<String, List<TaskInstanceInfo>> tasks(
            Collection<String> sourceAppNames,
            Set<TaskInstanceState> validStates,
            boolean skipStaleCheck) {
        if (sourceAppNames.isEmpty()) {
            return Map.of();
        }
        var stamp = lock.readLock();
        try {
            val availableApps = cache.keySet();
            if (!availableApps.containsAll(sourceAppNames)) {
                val status = lock.tryConvertToWriteLock(stamp);
                if (status == 0) { //Did not lock, try explicit lock
                    lock.unlockRead(stamp);
                    stamp = lock.writeLock();
                }
                else {
                    stamp = status;
                }
                val missingApps = Sets.difference(Set.copyOf(sourceAppNames), availableApps);
                log.info("Loading task data for: {}", missingApps);
                reloadTasksForApps(missingApps);
            }

            val validUpdateDate = new Date(System.currentTimeMillis() - MAX_ACCEPTABLE_UPDATE_INTERVAL.toMillis());
            return sourceAppNames.stream()
                    .map(cache::get)
                    .filter(Objects::nonNull)
                    .flatMap(instances -> instances.values().stream())
                    .filter(instanceInfo -> validStates.contains(instanceInfo.getState()))
                    .filter(instanceInfo -> skipStaleCheck || instanceInfo.getUpdated().after(validUpdateDate))
                    .collect(Collectors.groupingBy(TaskInstanceInfo::getSourceAppName,
                                                   Collectors.toUnmodifiableList()));
        }
        finally {
            lock.unlock(stamp);
        }
    }

    @Override
    public Optional<TaskInstanceInfo> task(String sourceAppName, String taskId) {
        return tasks(Set.of(sourceAppName), EnumSet.allOf(TaskInstanceState.class), true)
                .getOrDefault(sourceAppName, List.of())
                .stream()
                .filter(instanceInfo -> instanceInfo.getTaskId().equals(taskId))
                .findAny();
    }

    @Override
    protected boolean updateTaskImpl(String sourceAppName, String taskId, TaskInstanceInfo taskInstanceInfo) {
        val stamp = lock.writeLock();
        try {
            val existing = cache.getOrDefault(sourceAppName, Map.of()).get(taskId);
            if(existing != null && existing.getUpdated().after(taskInstanceInfo.getUpdated())) {
                log.warn("Ignoring stale update for {}/{}", sourceAppName, taskId);
                return false;
            }
            return updateTaskInternal(sourceAppName, taskId, taskInstanceInfo);
        }
        finally {
            lock.unlock(stamp);
        }
    }

    private boolean updateTaskInternal(String sourceAppName, String taskId, TaskInstanceInfo taskInstanceInfo) {
        val status = root.updateTaskImpl(sourceAppName, taskId, taskInstanceInfo);
        if (status) {
            cache.compute(sourceAppName, (aId, oldInstances) -> {
                val instances = null != oldInstances
                                ? oldInstances
                                : new HashMap<String, TaskInstanceInfo>();
                instances.put(taskId, taskInstanceInfo);
                return instances;
            });
        }
        return status;
    }

    @Override
    public boolean deleteTask(String sourceAppName, String taskId) {
        val stamp = lock.writeLock();
        try {
            val status = root.deleteTask(sourceAppName, taskId);
            if (status) {
                val instances = cache.get(sourceAppName);
                if (null != instances) {
                    instances.remove(taskId);
                }
            }
            return status;
        }
        finally {
            lock.unlock(stamp);
        }
    }

    @Override
    public Optional<TaskInstanceInfo> checkedCurrentState(String sourceAppName, String taskId) {
        val validUpdateDate = new Date(new Date().getTime() - MAX_ACCEPTABLE_UPDATE_INTERVAL.toMillis());
        var stamp = lock.readLock();
        try {
            val instance = task(sourceAppName, taskId).orElse(null);
            if (null == instance
                    || !ACTIVE_STATES.contains(instance.getState())
                    || instance.getUpdated().after(validUpdateDate)) {
                return Optional.ofNullable(instance);
            }
            // Ok need to update the task now...
            val status = lock.tryConvertToWriteLock(stamp);
            if (status == 0) { //Did not lock, try explicit lock
                lock.unlockRead(stamp);
                stamp = lock.writeLock();
            }
            else {
                stamp = status;
            }
            log.warn("Found stale task instance {}/{}. Current state: {} Last updated at: {}",
                     sourceAppName, instance.getTaskId(), instance.getState(), instance.getUpdated());
            val updateStatus = updateTaskInternal(
                    sourceAppName,
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
        }
        finally {
            lock.unlock(stamp);
        }
        return task(sourceAppName, taskId);
    }

    private void reloadTasksForApps(Collection<String> sourceAppNames) {
        val appsWithTasks = root.tasks(sourceAppNames, EnumSet.allOf(TaskInstanceState.class), true)
                .entrySet()
                .stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().stream()
                        .collect(Collectors.toMap(TaskInstanceInfo::getTaskId, Function.identity()))));
        cache.putAll(appsWithTasks);
        //For apps that don't have any running nodes (in monitoring states etc), add empty maps
        val appsWithoutTasks = Sets.difference(Set.copyOf(sourceAppNames), cache.keySet());
        appsWithoutTasks.forEach(sourceAppName -> cache.put(sourceAppName, new HashMap<>()));
        log.info("Loaded task data {}. Empty: {}", appsWithTasks.keySet(), appsWithoutTasks);
    }

    private void purge(boolean leader) {
        val stamp = lock.writeLock();
        try {
            cache.clear();
        }
        finally {
            lock.unlock(stamp);
        }
    }
}
