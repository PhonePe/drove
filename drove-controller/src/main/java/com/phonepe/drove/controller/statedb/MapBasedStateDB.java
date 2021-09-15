package com.phonepe.drove.controller.statedb;

import com.phonepe.drove.common.model.ExecutorState;
import com.phonepe.drove.models.application.ApplicationInfo;
import com.phonepe.drove.models.instance.InstanceInfo;
import lombok.Value;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 *
 */
@Value
public class MapBasedStateDB implements StateDB {
    Map<String, ExecutorState> executors = new ConcurrentHashMap<>();
    Map<String, ApplicationStateEntry> apps = new ConcurrentHashMap<>();


    @Override
    public List<ExecutorState> executorState(int start, int size) {
        //TODO:: THIS IS NOT PERFORMANT IN TERMS OF MEMORY
        return List.copyOf(executors.values()).subList(start, start + size);
    }

    @Override
    public boolean updateExecutorState(
            String executorId, ExecutorState executorState) {
        executors.compute(executorId, (id, oldValue) -> executorState);
        return true;
    }

    @Override
    public boolean deleteExecutorState(String executorId) {
        executors.remove(executorId);
        return true;
    }

    @Override
    public List<ApplicationInfo> applicationState(int start, int size) {
        //TODO:: THIS IS NOT PERFORMANT IN TERMS OF MEMORY
        return apps.values()
                .stream()
                .map(ApplicationStateEntry::getApplicationInfo)
                .collect(Collectors.toUnmodifiableList())
                .subList(start, start + size);
    }

    @Override
    public boolean updateApplicationState(
            String appId, ApplicationInfo applicationInfo) {
        apps.compute(appId, (id, oldValue) -> {
            if (null == oldValue) {
                return new ApplicationStateEntry(applicationInfo, new ConcurrentHashMap<>());
            }
            return new ApplicationStateEntry(applicationInfo, oldValue.getInstances());
        });
        return true;
    }

    @Override
    public boolean deleteApplicationState(String appId) {
        apps.remove(appId);
        return true;
    }

    @Override
    public List<InstanceInfo> applicationState(String appId, int start, int size) {
        //TODO:: THIS IS NOT PERFORMANT IN TERMS OF MEMORY
        return List.copyOf(apps.get(appId).getInstances().values())
                .subList(start, start + size);
    }

    @Override
    public boolean updateInstanceState(
            String appId, String instanceId, InstanceInfo instanceInfo) {
        //Do not replace this with what seems obvious, this keeps the op atomic
        apps.computeIfPresent(appId,
                              (id, value) -> {
                                  value.getInstances()
                                          .compute(instanceId,
                                                   (iid, oldValue) -> instanceInfo);
                                  return new ApplicationStateEntry(value.getApplicationInfo(),
                                                                   value.getInstances());
                              });
        return true;
    }

    @Override
    public boolean deleteInstanceState(String appId, String instanceId) {
        apps.computeIfPresent(appId, (id, value) -> {
            value.getInstances()
                    .remove(instanceId);
            return new ApplicationStateEntry(value.getApplicationInfo(), value.getInstances());
        });
        return true;
    }
}
