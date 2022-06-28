package com.phonepe.drove.controller.testsupport;

import com.phonepe.drove.controller.statedb.InstanceInfoDB;
import com.phonepe.drove.models.instance.InstanceInfo;
import com.phonepe.drove.models.instance.InstanceState;
import lombok.val;

import javax.inject.Singleton;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import static com.phonepe.drove.models.instance.InstanceState.ACTIVE_STATES;
import static com.phonepe.drove.models.instance.InstanceState.LOST;

/**
 *
 */
@Singleton
public class InMemoryInstanceInfoDB implements InstanceInfoDB {

    private final Map<String, Map<String, InstanceInfo>> instances = new ConcurrentHashMap<>();

    @Override
    public Map<String, List<InstanceInfo>> instances(
            Collection<String> appIds,
            Set<InstanceState> validStates,
            boolean skipStaleCheck) {
        val validUpdateDate = new Date(System.currentTimeMillis() - MAX_ACCEPTABLE_UPDATE_INTERVAL.toMillis());
        return appIds.stream()
                .map(instances::get)
                .filter(Objects::nonNull)
                .flatMap(instances -> instances.values().stream())
                .filter(instanceInfo -> validStates.contains(instanceInfo.getState()))
                .filter(instanceInfo -> skipStaleCheck || instanceInfo.getUpdated().after(validUpdateDate))
                .collect(Collectors.groupingBy(InstanceInfo::getAppId, Collectors.toUnmodifiableList()));
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
        val validUpdateDate = new Date(new Date().getTime() - MAX_ACCEPTABLE_UPDATE_INTERVAL.toMillis());
        //Find all instances in active states that have not been updated in stipulated time and move them to unknown state
        val instances = instances(appId, ACTIVE_STATES, 0, Integer.MAX_VALUE, true)
                .stream().filter(i -> i.getUpdated().before(validUpdateDate))
                .toList();
        instances.forEach(instanceInfo -> {
            updateInstanceState(appId,
                                instanceInfo.getInstanceId(),
                                new InstanceInfo(instanceInfo.getAppId(),
                                                 instanceInfo.getAppName(),
                                                 instanceInfo.getInstanceId(),
                                                 instanceInfo.getExecutorId(),
                                                 instanceInfo.getLocalInfo(),
                                                 instanceInfo.getResources(),
                                                 LOST,
                                                 instanceInfo.getMetadata(),
                                                 "Instance lost",
                                                 instanceInfo.getCreated(),
                                                 new Date()));
        });
        return instances.size();
    }

}
