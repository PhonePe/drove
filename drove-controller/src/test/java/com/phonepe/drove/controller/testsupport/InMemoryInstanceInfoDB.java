package com.phonepe.drove.controller.testsupport;

import com.phonepe.drove.controller.statedb.InstanceInfoDB;
import com.phonepe.drove.models.instance.InstanceInfo;
import com.phonepe.drove.models.instance.InstanceState;
import lombok.val;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 *
 */
public class InMemoryInstanceInfoDB implements InstanceInfoDB {

    private final Map<String, Map<String, InstanceInfo>> instances = new ConcurrentHashMap<>();

    @Override
    public List<InstanceInfo> activeInstances(String appId, Set<InstanceState> validStates, int start, int size) {
        return instances.getOrDefault(appId, Collections.emptyMap())
                .values()
                .stream()
                .filter(i -> validStates.contains(i.getState()))
                .toList();
    }

    @Override
    public Optional<InstanceInfo> instance(String appId, String instanceId) {
        return Optional.ofNullable(instances.getOrDefault(appId, Collections.emptyMap()).get(instanceId));
    }

    @Override
    public boolean updateInstanceState(String appId, String instanceId, InstanceInfo instanceInfo) {
        instances.compute(appId, (aId, old) -> {
            val ins = Objects.requireNonNullElse(old, new ConcurrentHashMap<String, InstanceInfo>());
            ins.put(instanceId, instanceInfo);
            return ins;
        });
        return true;
    }

    @Override
    public boolean deleteInstanceState(String appId, String instanceId) {
        return !instances.containsKey(appId) || instances.get(appId).remove(instanceId) != null;
    }

    @Override
    public boolean deleteAllInstancesForApp(String appId) {
        return instances.remove(appId) != null;
    }

    @Override
    public long markStaleInstances(String appId) {
        return 0;
    }

    @Override
    public List<InstanceInfo> oldInstances(String appId, int start, int size) {
        return Collections.emptyList();
    }
}
