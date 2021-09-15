package com.phonepe.drove.controller.statedb;

import com.phonepe.drove.common.model.ExecutorState;
import com.phonepe.drove.models.application.ApplicationInfo;
import com.phonepe.drove.models.instance.InstanceInfo;

import java.util.List;

/**
 *
 */
public interface StateDB {
    List<ExecutorState> executorState(int start, int size);
    boolean updateExecutorState(String executorId, final ExecutorState executorState);
    boolean deleteExecutorState(String executorId);

    List<ApplicationInfo> applicationState(int start, int size);
    boolean updateApplicationState(String appId, final ApplicationInfo applicationInfo);
    boolean deleteApplicationState(String appId);

    List<InstanceInfo> applicationState(String appId, int start, int size);
    boolean updateInstanceState(String appId, String instanceId, InstanceInfo instanceInfo);
    boolean deleteInstanceState(String appId, String instanceId);
}
