package com.phonepe.drove.controller.statedb;

import com.google.common.collect.Sets;
import com.phonepe.drove.models.instance.InstanceInfo;
import com.phonepe.drove.models.instance.InstanceState;

import java.util.*;

import static com.phonepe.drove.models.instance.InstanceState.ACTIVE_STATES;
import static com.phonepe.drove.models.instance.InstanceState.HEALTHY;

/**
 *
 */
public interface InstanceInfoDB {

    default List<InstanceInfo> healthyInstances(String appId) {
        return instances(appId, Set.of(HEALTHY), 0, Integer.MAX_VALUE);
    }

    default List<InstanceInfo> activeInstances(String appId, int start, int size) {
        return instances(appId, ACTIVE_STATES, start, size);
    }

    default List<InstanceInfo> oldInstances(String appId, int start, int size) {
        return instances(
                appId, Sets.difference(EnumSet.allOf(InstanceState.class), ACTIVE_STATES), start, size, true);
    }

    default long instanceCount(final String appId, InstanceState requiredState) {
        return instanceCount(appId, Collections.singleton(requiredState));
    }

    default long instanceCount(final String appId, Set<InstanceState> requiredStates) {
        return instances(appId, requiredStates, 0, Integer.MAX_VALUE).size();
    }

    default List<InstanceInfo> instances(String appId, Set<InstanceState> validStates, int start, int size) {
        return instances(appId, validStates, start, size, false);
    }

    List<InstanceInfo> instances(String appId, Set<InstanceState> validStates, int start, int size, boolean skipStaleCheck);

    Optional<InstanceInfo> instance(String appId, String instanceId);

    boolean updateInstanceState(String appId, String instanceId, InstanceInfo instanceInfo);

    boolean deleteInstanceState(String appId, String instanceId);

    boolean deleteAllInstancesForApp(String appId);

    long markStaleInstances(final String appId);

}
