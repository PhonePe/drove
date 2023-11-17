package com.phonepe.drove.controller.statedb;

import com.google.common.collect.Sets;
import com.phonepe.drove.common.model.utils.Pair;
import com.phonepe.drove.controller.managed.LeadershipEnsurer;
import com.phonepe.drove.models.taskinstance.TaskInfo;
import com.phonepe.drove.models.taskinstance.TaskState;
import io.appform.functionmetrics.MonitoredFunction;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import java.util.*;
import java.util.concurrent.locks.StampedLock;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 *
 */
@Slf4j
@Singleton
public class CachingProxyTaskDB extends TaskDB {
    private final TaskDB root;

    private final Map<String, Map<String, TaskInfo>> cache = new HashMap<>();
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
    @MonitoredFunction
    public Map<String, List<TaskInfo>> tasks(
            Collection<String> sourceAppNames,
            Set<TaskState> validStates,
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
                    .collect(Collectors.groupingBy(TaskInfo::getSourceAppName,
                                                   Collectors.toUnmodifiableList()));
        }
        finally {
            lock.unlock(stamp);
        }
    }

    @Override
    @MonitoredFunction
    public void cleanupTasks(Predicate<TaskInfo> handler) {
        val stamp = lock.writeLock();
        try {
            val deleted = new ArrayList<Pair<String, String>>();
            cache.forEach((appName, tasks) -> tasks.forEach((taskId, task) -> {
                if(handler.test(task) && root.deleteTask(appName, taskId)) {
                    log.debug("Removed task info {}/{} from root", appName, taskId);
                    deleted.add(new Pair<>(appName, taskId));
                }
            }));
            deleted.forEach(pair -> {
                cache.get(pair.getFirst()).remove(pair.getSecond());
                log.debug("Removed task info: {}/{}", pair.getFirst(), pair.getSecond());
            });
        }
        finally {
            lock.unlock(stamp);
        }
    }

    @Override
    @MonitoredFunction
    public Optional<TaskInfo> task(String sourceAppName, String taskId) {
        return tasks(Map.of(sourceAppName, Set.of(taskId)), TaskState.ALL)
                .stream()
                .findAny();
    }

    @Override
    @MonitoredFunction(method = "update")
    protected boolean updateTaskImpl(String sourceAppName, String taskId, TaskInfo taskInfo) {
        val stamp = lock.writeLock();
        try {
            val existing = cache.getOrDefault(sourceAppName, Map.of()).get(taskId);
            if(existing != null && existing.getUpdated().after(taskInfo.getUpdated())) {
                log.warn("Ignoring stale update for {}/{}", sourceAppName, taskId);
                return false;
            }
            val status = root.updateTaskImpl(sourceAppName, taskId, taskInfo);
            if (status) {
                cache.compute(sourceAppName, (aId, oldInstances) -> {
                    val instances = null != oldInstances
                                    ? oldInstances
                                    : new HashMap<String, TaskInfo>();
                    instances.put(taskId, taskInfo);
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
    @MonitoredFunction
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

    private void reloadTasksForApps(Collection<String> sourceAppNames) {
        val appsWithTasks = root.tasks(sourceAppNames, TaskState.ALL, true)
                .entrySet()
                .stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().stream()
                        .collect(Collectors.toMap(TaskInfo::getTaskId, Function.identity()))));
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
