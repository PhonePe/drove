package com.phonepe.drove.controller.statedb;

import com.phonepe.drove.models.instance.InstanceInfo;
import com.phonepe.drove.models.instance.InstanceState;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 *
 */
public interface InstanceInfoDB {
    default List<InstanceInfo> healthyInstances(String appId) {
        return activeInstances(appId, 0, Integer.MAX_VALUE)
                .stream()
                .filter(instanceInfo -> instanceInfo.getState().equals(InstanceState.HEALTHY))
                .toList();
    }

    List<InstanceInfo> activeInstances(String appId, int start, int size);

    List<InstanceInfo> oldInstances(String appId, int start, int size);

    Optional<InstanceInfo> instance(String appId, String instanceId);

    default long instanceCount(final String appId, InstanceState requiredState) {
        return instanceCount(appId, Collections.singleton(requiredState));
    }

    default long instanceCount(final String appId, Set<InstanceState> requiredStates) {
        return activeInstances(appId, 0, Integer.MAX_VALUE)
                .stream()
                .filter(instance -> requiredStates.contains(instance.getState()))
                .count();
    }

    boolean updateInstanceState(String appId, String instanceId, InstanceInfo instanceInfo);

    boolean deleteInstanceState(String appId, String instanceId);

    boolean deleteAllInstancesForApp(String appId);
}
