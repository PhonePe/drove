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

import com.google.common.collect.Sets;
import com.phonepe.drove.models.instance.LocalServiceInstanceState;
import com.phonepe.drove.models.localservice.LocalServiceInfo;
import com.phonepe.drove.models.localservice.LocalServiceInstanceInfo;
import io.appform.functionmetrics.MonitoredFunction;

import java.time.Duration;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 *
 */
public interface LocalServiceStateDB {
    Duration MAX_ACCEPTABLE_UPDATE_INTERVAL = Duration.ofMinutes(1);

    Optional<LocalServiceInfo> service(String serviceId);

    List<LocalServiceInfo> services(int start, int size);

    boolean updateService(final String serviceId,
                          final LocalServiceInfo info);

    boolean removeService(final String serviceId);

    Optional<LocalServiceInstanceInfo> instance(String serviceId, String instanceId);
    List<LocalServiceInstanceInfo> instances(String serviceId, Set<LocalServiceInstanceState> states,
                                             boolean skipStaleCheck);

    default List<LocalServiceInstanceInfo> oldInstances(final String serviceId) {
        return instances(serviceId, Sets.difference(EnumSet.allOf(LocalServiceInstanceState.class),
                                                    LocalServiceInstanceState.ACTIVE_STATES),
                         true);
    }

    @MonitoredFunction
    boolean deleteInstanceState(String serviceId, String instanceId);

    @MonitoredFunction
    boolean deleteAllInstancesForService(String serviceId);

    boolean updateInstanceState(String serviceId, String instanceId, LocalServiceInstanceInfo instanceInfo);

    long markStaleInstances(String serviceId);

}
