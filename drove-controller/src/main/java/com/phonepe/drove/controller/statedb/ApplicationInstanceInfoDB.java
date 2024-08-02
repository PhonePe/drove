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
import com.phonepe.drove.models.instance.InstanceInfo;
import com.phonepe.drove.models.instance.InstanceState;

import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

import static com.phonepe.drove.common.CommonUtils.sublist;
import static com.phonepe.drove.models.instance.InstanceState.ACTIVE_STATES;
import static com.phonepe.drove.models.instance.InstanceState.HEALTHY;

/**
 *
 */
public interface ApplicationInstanceInfoDB {

    Duration MAX_ACCEPTABLE_UPDATE_INTERVAL = Duration.ofMinutes(1);

    default Map<String, List<InstanceInfo>> healthyInstances(Collection<String> appIds) {
        return instances(appIds, Set.of(HEALTHY));
    }

    default List<InstanceInfo> healthyInstances(String appId) {
        return instances(appId, Set.of(HEALTHY), 0, Integer.MAX_VALUE);
    }

    default Map<String, List<InstanceInfo>> activeInstances(Collection<String> appIds) {
        return instances(appIds, ACTIVE_STATES);
    }

    default List<InstanceInfo> activeInstances(String appId, int start, int size) {
        return instances(appId, ACTIVE_STATES, start, size);
    }

    default Map<String,List<InstanceInfo>> oldInstances(Collection<String> appIds) {
        return instances(
                appIds, Sets.difference(EnumSet.allOf(InstanceState.class), ACTIVE_STATES), true);
    }

    default List<InstanceInfo> oldInstances(String appId, int start, int size) {
        return instances(
                appId, Sets.difference(EnumSet.allOf(InstanceState.class), ACTIVE_STATES), start, size, true);
    }

    default Map<String,Long> instanceCount(final Collection<String> appIds, InstanceState requiredState) {
        return instanceCount(appIds, Set.of(requiredState));
    }

    default Map<String,Long> instanceCount(final Collection<String> appIds, Set<InstanceState> requiredStates) {
        return instances(appIds, requiredStates)
                .entrySet()
                .stream()
                .flatMap(e -> e.getValue().stream())
                .collect(Collectors.groupingBy(InstanceInfo::getAppId, Collectors.counting()));
    }

    default long instanceCount(final String appId, InstanceState requiredState) {
        return instanceCount(appId, Set.of(requiredState));
    }

    default long instanceCount(final String appId, Set<InstanceState> requiredStates) {
        return instanceCount(Set.of(appId), requiredStates).getOrDefault(appId, 0L);
    }

    default List<InstanceInfo> instances(String appId, Set<InstanceState> validStates, int start, int size) {
        return instances(appId, validStates, start, size, false);
    }

    default List<InstanceInfo> instances(
            String appId,
            Set<InstanceState> validStates,
            int start,
            int size,
            boolean skipStaleCheck) {
        return sublist(instances(Set.of(appId), validStates, skipStaleCheck).getOrDefault(appId, List.of()), start, size);
    }

    default Map<String, List<InstanceInfo>> instances(Collection<String> appIds, Set<InstanceState> validStates) {
        return instances(appIds, validStates, false);
    }

    Map<String, List<InstanceInfo>> instances(
            Collection<String> appIds, Set<InstanceState> validStates, boolean skipStaleCheck);

    Optional<InstanceInfo> instance(String appId, String instanceId);

    boolean updateInstanceState(String appId, String instanceId, InstanceInfo instanceInfo);

    boolean deleteInstanceState(String appId, String instanceId);

    boolean deleteAllInstancesForApp(String appId);

    long markStaleInstances(final String appId);

}
