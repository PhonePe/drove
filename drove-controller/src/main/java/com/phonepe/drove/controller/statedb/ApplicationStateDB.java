package com.phonepe.drove.controller.statedb;

import com.phonepe.drove.models.application.ApplicationInfo;
import com.phonepe.drove.models.instance.InstanceInfo;
import com.phonepe.drove.models.instance.InstanceState;

import java.util.List;
import java.util.Optional;

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
                                                    appInfo.getUpdated()))
                .map(appInfo -> updateApplicationState(appId, appInfo))
                .orElse(false);
    }

    boolean deleteApplicationState(String appId);

    List<InstanceInfo> instances(String appId, int start, int size);

    Optional<InstanceInfo> instance(String appId, String instanceId);

    default long instanceCount(final String appId) {
        return instances(appId, 0, Integer.MAX_VALUE)
                .stream()
                .filter(instance -> instance.getState().equals(InstanceState.HEALTHY))
                .count();
    }

    boolean updateInstanceState(String appId, String instanceId, InstanceInfo instanceInfo);

    boolean deleteInstanceState(String appId, String instanceId);
}
