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

package com.phonepe.drove.controller.statedb;

import com.google.common.collect.Sets;
import com.phonepe.drove.controller.managed.LeadershipEnsurer;
import com.phonepe.drove.models.instance.InstanceInfo;
import com.phonepe.drove.models.instance.InstanceState;
import io.appform.functionmetrics.MonitoredFunction;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import java.util.*;
import java.util.concurrent.locks.StampedLock;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 *
 */
@Singleton
@Slf4j
public class CachingProxyApplicationInstanceInfoDB implements ApplicationInstanceInfoDB {
    private final ApplicationInstanceInfoDB root;

    private final Map<String, Map<String, InstanceInfo>> cache = new HashMap<>();
    private final StampedLock lock = new StampedLock();

    @Inject
    public CachingProxyApplicationInstanceInfoDB(
            @Named("StoredInstanceInfoDB") final ApplicationInstanceInfoDB root,
            final LeadershipEnsurer leadershipEnsurer) {
        this.root = root;
        leadershipEnsurer.onLeadershipStateChanged().connect(this::purge);
        log.info("Created object");
    }

   @Override
   @MonitoredFunction
    public Map<String, List<InstanceInfo>> instances(
            Collection<String> appIds,
            Set<InstanceState> validStates,
            boolean skipStaleCheck) {
        if(appIds.isEmpty()) {
            return Map.of();
        }
        var stamp = lock.readLock();
        try {
            val availableApps = cache.keySet();
            if(!availableApps.containsAll(appIds)) {
                val status = lock.tryConvertToWriteLock(stamp);
                if (status == 0) { //Did not lock, try explicit lock
                    lock.unlockRead(stamp);
                    stamp = lock.writeLock();
                }
                else {
                    stamp = status;
                }
                val missingApps = Sets.difference(Set.copyOf(appIds), availableApps);
                log.info("Loading instance data for: {}", missingApps);
                reloadInstancesForApps(missingApps);
            }

            val validUpdateDate = new Date(System.currentTimeMillis() - MAX_ACCEPTABLE_UPDATE_INTERVAL.toMillis());
            return appIds.stream()
                    .map(cache::get)
                    .filter(Objects::nonNull)
                    .flatMap(instances -> instances.values().stream())
                    .filter(instanceInfo -> validStates.contains(instanceInfo.getState()))
                    .filter(instanceInfo -> skipStaleCheck || instanceInfo.getUpdated().after(validUpdateDate))
                    .collect(Collectors.groupingBy(InstanceInfo::getAppId, Collectors.toUnmodifiableList()));
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
                cache.compute(appId, (aId, oldInstances) -> {
                    val instances = null != oldInstances
                                    ? oldInstances
                                    : new HashMap<String, InstanceInfo>();
                    instances.put(instanceId, instanceInfo);
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
                reloadInstancesForApps(List.of(appId));
            }
            return count;
        }
        finally {
            lock.unlock(stamp);
        }
    }

    private void reloadInstancesForApps(Collection<String> appIds) {
        val appsWithInstances = root.instances(appIds, EnumSet.allOf(InstanceState.class), true)
                .entrySet()
                .stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().stream()
                        .collect(Collectors.toMap(InstanceInfo::getInstanceId, Function.identity()))));
        cache.putAll(appsWithInstances);
        //For apps that don't have any running nodes (in monitoring states etc), add empty maps
        val appsWithoutInstances = Sets.difference(Set.copyOf(appIds), cache.keySet());
        appsWithoutInstances.forEach(appId -> cache.put(appId, new HashMap<>()));
        log.info("Loaded app instance data {}. Empty: {}", appsWithInstances.keySet(), appsWithoutInstances);
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
