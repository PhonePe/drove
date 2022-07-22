package com.phonepe.drove.controller.statedb;

import com.google.common.collect.Sets;
import com.phonepe.drove.controller.managed.LeadershipEnsurer;
import com.phonepe.drove.models.taskinstance.TaskInstanceInfo;
import com.phonepe.drove.models.taskinstance.TaskInstanceState;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

import javax.inject.Named;
import java.util.*;
import java.util.concurrent.locks.StampedLock;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 *
 */
@Slf4j
public class CachingProxyTaskDB implements TaskDB {
    private final TaskDB root;

    private final Map<String, Map<String, TaskInstanceInfo>> cache = new HashMap<>();
    private final StampedLock lock = new StampedLock();

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
    public Optional<TaskInstanceInfo> instance(String sourceAppName, String taskId) {
        return tasks(Set.of(sourceAppName), EnumSet.allOf(TaskInstanceState.class), true)
                .getOrDefault(sourceAppName, List.of())
                .stream()
                .filter(instanceInfo -> instanceInfo.getTaskId().equals(taskId))
                .findAny();
    }

    @Override
    public boolean updateTask(String sourceAppName, String taskId, TaskInstanceInfo taskInstanceInfo) {
        val stamp = lock.writeLock();
        try {
            val status = root.updateTask(sourceAppName, taskId, taskInstanceInfo);
            if (status) {
                cache.compute(sourceAppName, (aId, oldInstances) -> {
                    val instances = null != oldInstances
                                    ? oldInstances
                                    : new HashMap<String, TaskInstanceInfo>();
                    instances.put(sourceAppName, taskInstanceInfo);
                    return instances;
                });
            }
            return status;
        }
        finally {
            lock.unlock(stamp);
        }
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
    public long markStaleTask(String sourceAppName) {
        val stamp = lock.writeLock();
        try {
            val count = root.markStaleTask(sourceAppName);
            if (count > 0) {
                reloadTasksForApps(List.of(sourceAppName));
            }
            return count;
        }
        finally {
            lock.unlock(stamp);
        }
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
