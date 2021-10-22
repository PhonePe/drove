package com.phonepe.drove.controller.statedb;

import com.phonepe.drove.models.application.ApplicationInfo;
import com.phonepe.drove.models.instance.InstanceInfo;

import java.util.List;
import java.util.Optional;

/**
 *
 */
public interface ApplicationStateDB {
    List<ApplicationInfo> applications(int start, int size);
    boolean updateApplicationState(String appId, final ApplicationInfo applicationInfo);
    boolean deleteApplicationState(String appId);

    List<InstanceInfo> instances(String appId, int start, int size);

    Optional<InstanceInfo> instance(String appId, String instanceId);

    boolean updateInstanceState(String appId, String instanceId, InstanceInfo instanceInfo);
    boolean deleteInstanceState(String appId, String instanceId);
}
