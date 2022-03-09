package com.phonepe.drove.controller.statedb;

import com.phonepe.drove.models.instance.InstanceInfo;
import com.phonepe.drove.models.instance.InstanceState;

import java.util.*;

import static com.phonepe.drove.models.instance.InstanceState.*;

/**
 *
 */
public interface InstanceInfoDB {

    default List<InstanceInfo> healthyInstances(String appId) {
        return activeInstances(appId, Collections.singleton(HEALTHY), 0, Integer.MAX_VALUE);
    }

    List<InstanceInfo> activeInstances(String appId, Set<InstanceState> validStates, int start, int size);

    default List<InstanceInfo> activeInstances(String appId, int start, int size) {
        return activeInstances(appId, ACTIVE_STATES, start, size);
    }

    Optional<InstanceInfo> instance(String appId, String instanceId);

    default long instanceCount(final String appId, InstanceState requiredState) {
        return instanceCount(appId, Collections.singleton(requiredState));
    }

    default long instanceCount(final String appId, Set<InstanceState> requiredStates) {
        return activeInstances(appId, requiredStates, 0, Integer.MAX_VALUE).size();
    }

    boolean updateInstanceState(String appId, String instanceId, InstanceInfo instanceInfo);

    boolean deleteInstanceState(String appId, String instanceId);

    boolean deleteAllInstancesForApp(String appId);

    long pruneStaleInstances(final String appId);

    List<InstanceInfo> oldInstances(String appId, int start, int size);
}
