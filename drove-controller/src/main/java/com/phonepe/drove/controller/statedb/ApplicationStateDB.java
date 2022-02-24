package com.phonepe.drove.controller.statedb;

import com.phonepe.drove.models.application.ApplicationInfo;
import com.phonepe.drove.models.instance.InstanceInfo;
import com.phonepe.drove.models.instance.InstanceState;

import java.util.*;
import java.util.stream.Collectors;

/**
 *
 */
public interface ApplicationStateDB {
    List<ApplicationInfo> applications(int start, int size);

    Optional<ApplicationInfo> application(String appId);

    boolean updateApplicationState(String appId, final ApplicationInfo applicationInfo);

    default boolean updateInstanceCount(String appId, long instances) {
        return application(appId)
                .map(appInfo -> new ApplicationInfo(appId,
                                                    appInfo.getSpec(),
                                                    instances,
                                                    appInfo.getCreated(),
                                                    new Date()))
                .map(appInfo -> updateApplicationState(appId, appInfo))
                .orElse(false);
    }

    boolean deleteApplicationState(String appId);

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
}
