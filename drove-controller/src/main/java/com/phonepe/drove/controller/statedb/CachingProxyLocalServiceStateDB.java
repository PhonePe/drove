/*
 *  Copyright (c) 2024 Original Author(s), PhonePe India Pvt. Ltd.
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

import com.phonepe.drove.controller.managed.LeadershipEnsurer;
import com.phonepe.drove.models.instance.LocalServiceInstanceState;
import com.phonepe.drove.models.localservice.LocalServiceInfo;
import com.phonepe.drove.models.localservice.LocalServiceInstanceInfo;
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

import static com.phonepe.drove.common.CommonUtils.sublist;

/**
 *
 */
@Singleton
@Slf4j
public class CachingProxyLocalServiceStateDB implements LocalServiceStateDB {
    private final LocalServiceStateDB root;

    private final Map<String, LocalServiceInfo> stateCache = new HashMap<>();
    private final Map<String, Map<String, LocalServiceInstanceInfo>> instancesCache = new HashMap<>();

    private final StampedLock lock = new StampedLock();

    @Inject
    public CachingProxyLocalServiceStateDB(
            @Named("StoredLocalServiceDB") final LocalServiceStateDB root,
            final LeadershipEnsurer leadershipEnsurer) {
        this.root = root;
        leadershipEnsurer.onLeadershipStateChanged().connect(this::purge);
    }

    @Override
    public Optional<LocalServiceInfo> service(String serviceId) {
        return Optional.empty();
    }

    @Override
    public List<LocalServiceInfo> services(int start, int size) {
        var stamp = lock.readLock();
        try {
            if (stateCache.isEmpty()) {
                val status = lock.tryConvertToWriteLock(stamp);
                if (status == 0) { //Did not loc, try explicit lock
                    lock.unlockRead(stamp);
                    stamp = lock.writeLock();
                }
                else {
                    stamp = status;
                }
                loadServices();
            }
            return sublist(stateCache.values().stream().toList(), start, size);
        }
        finally {
            lock.unlock(stamp);
        }
    }

    @Override
    public boolean updateService(String serviceId, LocalServiceInfo info) {
        val stamp = lock.writeLock();
        try {
            val status = root.updateService(serviceId, info);
            if (status) {
                loadService(serviceId);
            }
            return status;
        }
        finally {
            lock.unlock(stamp);
        }
    }

    @Override
    public boolean removeService(String serviceId) {
        val stamp = lock.writeLock();
        try {
            val status = root.removeService(serviceId);
            if (status) {
                stateCache.remove(serviceId);
            }
            return status;
        }
        finally {
            lock.unlock(stamp);
        }
    }

    @Override
    public Optional<LocalServiceInstanceInfo> instance(String serviceId, String instanceId) {
        return instances(serviceId, EnumSet.allOf(LocalServiceInstanceState.class), true)
                .stream()
                .filter(instanceInfo -> instanceInfo.getInstanceId().equals(instanceId))
                .findAny();
    }

    @Override
    public List<LocalServiceInstanceInfo> instances(
            String serviceId,
            Set<LocalServiceInstanceState> states,
            boolean skipStaleCheck) {
        var stamp = lock.readLock();
        try {
            if (!instancesCache.containsKey(serviceId)) {
                val status = lock.tryConvertToWriteLock(stamp);
                if (status == 0) { //Did not lock, try explicit lock
                    lock.unlockRead(stamp);
                    stamp = lock.writeLock();
                }
                else {
                    stamp = status;
                }
                reloadInstancesForService(serviceId);
            }

            val validUpdateDate = new Date(System.currentTimeMillis() - MAX_ACCEPTABLE_UPDATE_INTERVAL.toMillis());
            return instancesCache.getOrDefault(serviceId, Map.of())
                    .values()
                    .stream()
                    .filter(instanceInfo -> states.contains(instanceInfo.getState()))
                    .filter(instanceInfo -> skipStaleCheck || instanceInfo.getUpdated().after(validUpdateDate))
                    .toList();
        }
        finally {
            lock.unlock(stamp);
        }
    }

    @MonitoredFunction
    @Override
    public boolean deleteInstanceState(String serviceId, String instanceId) {
        val stamp = lock.writeLock();
        try {
            val status = root.deleteInstanceState(serviceId, instanceId);
            if (status) {
                removeInstanceCacheEntry(serviceId, instanceId);
            }
            return status;
        }
        finally {
            lock.unlock(stamp);
        }
    }

    @MonitoredFunction
    @Override
    public boolean deleteAllInstancesForService(String serviceId) {
        val stamp = lock.writeLock();
        try {
            val status = root.deleteAllInstancesForService(serviceId);
            if (status) {
                instancesCache.remove(serviceId);
            }
            return status;
        }
        finally {
            lock.unlock(stamp);
        }
    }

    @Override
    public boolean updateInstanceState(String serviceId, String instanceId, LocalServiceInstanceInfo instanceInfo) {
        val stamp = lock.writeLock();
        try {
            val status = root.updateInstanceState(serviceId, instanceId, instanceInfo);
            if (status) {
                instancesCache.compute(serviceId, (aId, oldInstances) -> {
                    val instances = null != oldInstances
                                    ? oldInstances
                                    : new HashMap<String, LocalServiceInstanceInfo>();
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

    private void purge(boolean leader) {
        val stamp = lock.writeLock();
        try {
            stateCache.clear();
            instancesCache.clear();
        }
        finally {
            lock.unlock(stamp);
        }
    }

    private void loadServices() {
        log.info("Loading app info for all apps");
        stateCache.clear();
        stateCache.putAll(root.services(0, Integer.MAX_VALUE)
                                  .stream()
                                  .collect(Collectors.toMap(LocalServiceInfo::getServiceId, Function.identity())));
    }

    private void loadService(String serviceId) {
        log.info("Loading app info for {}", serviceId);
        val service = root.service(serviceId).orElse(null);
        if (null == service) {
            stateCache.remove(serviceId);
        }
        else {
            stateCache.put(serviceId, service);
        }
    }

    private void reloadInstancesForService(String serviceId) {
        val instances = root.instances(serviceId, EnumSet.allOf(LocalServiceInstanceState.class), true)
                .stream()
                .collect(Collectors.toMap(LocalServiceInstanceInfo::getInstanceId, Function.identity()));
        instancesCache.put(serviceId, instances);
    }

    private void removeInstanceCacheEntry(String serviceId, String instanceId) {
        val instances = instancesCache.get(serviceId);
        if (null != instances) {
            instances.remove(instanceId);
            if (instances.isEmpty()) {
                instancesCache.remove(serviceId);
                log.debug("Removing cache key: {}", serviceId);
            }
        }
    }
}
