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

package com.phonepe.drove.controller.testsupport;

import com.phonepe.drove.controller.statedb.LocalServiceStateDB;
import com.phonepe.drove.models.instance.LocalServiceInstanceState;
import com.phonepe.drove.models.localservice.LocalServiceInfo;
import com.phonepe.drove.models.localservice.LocalServiceInstanceInfo;
import lombok.val;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 *
 */
public class InMemoryLocalServiceStateDB implements LocalServiceStateDB {
    private final Map<String, LocalServiceInfo> services = new ConcurrentHashMap<>();
    private final Map<String, Map<String, LocalServiceInstanceInfo>> instances = new ConcurrentHashMap<>();

    @Override
    public Optional<LocalServiceInfo> service(String serviceId) {
        return Optional.ofNullable(services.get(serviceId));
    }

    @Override
    public List<LocalServiceInfo> services(int start, int size) {
        return List.copyOf(services.values());
    }

    @Override
    public boolean updateService(String serviceId, LocalServiceInfo info) {
        services.put(serviceId, info);
        return true;
    }

    @Override
    public boolean removeService(String serviceId) {
        return services.remove(serviceId) != null;
    }

    @Override
    public Optional<LocalServiceInstanceInfo> instance(String serviceId, String instanceId) {
        return Optional.ofNullable(instances.getOrDefault(serviceId, Map.of()).get(instanceId));
    }

    @Override
    public List<LocalServiceInstanceInfo> instances(
            String serviceId,
            Set<LocalServiceInstanceState> states,
            boolean skipStaleCheck) {
        return List.copyOf(instances.getOrDefault(serviceId, Map.of()).values());
    }

    @Override
    public boolean deleteInstanceState(String serviceId, String instanceId) {
        return instances.getOrDefault(serviceId, Map.of()).remove(instanceId) != null;
    }

    @Override
    public boolean deleteAllInstancesForService(String serviceId) {
        return instances.remove(serviceId) != null;
    }

    @Override
    public boolean updateInstanceState(String serviceId, String instanceId, LocalServiceInstanceInfo instanceInfo) {
        instances.computeIfAbsent(serviceId, sId -> new ConcurrentHashMap<>()).put(instanceId, instanceInfo);
        return true;
    }

    @Override
    public long markStaleInstances(String serviceId) {
        val validUpdateDate = new Date(new Date().getTime() - MAX_ACCEPTABLE_UPDATE_INTERVAL.toMillis());
        //Find all instances in active states that have not been updated in stipulated time and move them to unknown
        // state
        val instances = this.instances.getOrDefault(serviceId, Map.of()).values().stream()
                .filter(instanceInfo -> LocalServiceInstanceState.ACTIVE_STATES.contains(instanceInfo.getState())
                        && instanceInfo.getUpdated().before(validUpdateDate))
                .toList();

        instances.forEach(instanceInfo -> updateInstanceState(serviceId,
                                                              instanceInfo.getInstanceId(),
                                                              instanceInfo.withState(LocalServiceInstanceState.LOST)
                                                                      .withErrorMessage("Instance lost")
                                                                      .withUpdated(new Date())));
        return instances.size();
    }
}
