package com.phonepe.drove.controller.statedb;

import com.phonepe.drove.controller.managed.LeadershipEnsurer;
import com.phonepe.drove.models.instance.InstanceInfo;
import com.phonepe.drove.models.instance.InstanceState;
import io.appform.functionmetrics.MonitoredFunction;
import lombok.val;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import java.util.*;
import java.util.concurrent.locks.StampedLock;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.phonepe.drove.common.CommonUtils.sublist;

/**
 *
 */
@Singleton
public class CachingProxyInstanceInfoDB implements InstanceInfoDB {
    private final InstanceInfoDB root;

    private final Map<String, Map<String, InstanceInfo>> cache = new HashMap<>();
    private final StampedLock lock = new StampedLock();

    @Inject
    public CachingProxyInstanceInfoDB(@Named("StoredInstanceInfoDB") final InstanceInfoDB root,
                                      final LeadershipEnsurer leadershipEnsurer) {
        this.root = root;
        leadershipEnsurer.onLeadershipStateChanged().connect(this::purge);
    }

    @Override
    @MonitoredFunction
    public List<InstanceInfo> instances(
            String appId,
            Set<InstanceState> validStates,
            int start,
            int size,
            boolean skipStaleCheck) {
        var stamp = lock.readLock();
        try {
            var appInstances = cache.get(appId);
            if (appInstances == null || appInstances.isEmpty()) {
                val status = lock.tryConvertToWriteLock(stamp);
                if (status == 0) { //Did not lock, try explicit lock
                    lock.unlockRead(stamp);
                    stamp = lock.writeLock();
                }
                else {
                    stamp = status;
                }
                //Definitely locked for write here
                appInstances = reloadInstancesForApp(appId);
            }
            val validUpdateDate = new Date(System.currentTimeMillis() - MAX_ACCEPTABLE_UPDATE_INTERVAL.toMillis());

            return sublist(appInstances.values()
                                   .stream()
                                   .filter(instanceInfo -> validStates.contains(instanceInfo.getState()))
                                   .filter(instanceInfo -> skipStaleCheck || instanceInfo.getUpdated().after(validUpdateDate))
                                   .toList(), start, size);
        }
        finally {
            lock.unlock(stamp);
        }
    }

    @Override
    @MonitoredFunction
    public Optional<InstanceInfo> instance(String appId, String instanceId) {
        return instances(appId, EnumSet.allOf(InstanceState.class), 0, Integer.MAX_VALUE, true)
                .stream()
                .filter(instanceInfo -> instanceInfo.getInstanceId().equals(instanceId))
                .findAny();
    }

    @Override
    @MonitoredFunction
    public boolean updateInstanceState(String appId, String instanceId, InstanceInfo instanceInfo) {
        val stamp = lock.writeLock();
        try {
            val status = root.updateInstanceState(appId, instanceId, instanceInfo);
            if (status) {
                reloadInstancesForApp(appId);
            }
            return status;
        }
        finally {
            lock.unlock(stamp);
        }
    }

    @Override
    @MonitoredFunction
    public boolean deleteInstanceState(String appId, String instanceId) {
        val stamp = lock.writeLock();
        try {
            val status = root.deleteInstanceState(appId, instanceId);
            if (status) {
                val instances = cache.get(appId);
                if (null != instances) {
                    instances.remove(instanceId);
                }
            }
            return status;
        }
        finally {
            lock.unlock(stamp);
        }
    }

    @Override
    @MonitoredFunction
    public boolean deleteAllInstancesForApp(String appId) {
        val stamp = lock.writeLock();
        try {
            val status = root.deleteAllInstancesForApp(appId);
            if (status) {
                cache.remove(appId);
            }
            return status;
        }
        finally {
            lock.unlock(stamp);
        }
    }

    @Override
    @MonitoredFunction
    public long markStaleInstances(String appId) {
        val stamp = lock.writeLock();
        try {
            val count = root.markStaleInstances(appId);
            if (count > 0) {
                reloadInstancesForApp(appId);
            }
            return count;
        }
        finally {
            lock.unlock(stamp);
        }
    }

    private HashMap<String, InstanceInfo> reloadInstancesForApp(String appId) {
        val instances = new HashMap<>(root.instances(appId,
                                                     EnumSet.allOf(InstanceState.class),
                                                     0,
                                                     Integer.MAX_VALUE,
                                                     true)
                                              .stream()
                                              .collect(Collectors.toMap(InstanceInfo::getInstanceId,
                                                                        Function.identity())));
        cache.put(appId, instances);
        return instances;
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
