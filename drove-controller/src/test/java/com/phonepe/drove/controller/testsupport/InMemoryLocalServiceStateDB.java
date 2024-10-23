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

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
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
}
