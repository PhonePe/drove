package com.phonepe.drove.controller.statedb;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.phonepe.drove.common.zookeeper.ZkUtils;
import com.phonepe.drove.models.application.ApplicationInfo;
import com.phonepe.drove.models.instance.InstanceInfo;
import com.phonepe.drove.models.instance.InstanceState;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.apache.curator.framework.CuratorFramework;
import org.apache.zookeeper.KeeperException;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import static com.phonepe.drove.common.CommonUtils.sublist;

/**
 *
 */
@Slf4j
@Singleton
public class MapBasedApplicationStateDB implements ApplicationStateDB {
    private static final Set<InstanceState> DEAD_STATES = EnumSet.of(InstanceState.UNKNOWN, InstanceState.STOPPED);
    private static final String APPLICATION_STATE_PATH = "/applications";
    private static final String INSTANCE_STATE_PATH = "/instances";

    private final CuratorFramework curatorFramework;
    private final ObjectMapper mapper;
    private final Map<String, Map<String, InstanceInfo>> appInstances = new ConcurrentHashMap<>();
    private final Map<String, Map<String, InstanceInfo>> oldInstances = new ConcurrentHashMap<>();

    @Inject
    public MapBasedApplicationStateDB(
            CuratorFramework curatorFramework,
            ObjectMapper mapper) {
        this.curatorFramework = curatorFramework;
        this.mapper = mapper;
    }

    @Override
    @SneakyThrows
    public List<ApplicationInfo> applications(int start, int size) {
        try {
            return ZkUtils.readChildrenNodes(curatorFramework,
                                             APPLICATION_STATE_PATH,
                                             start,
                                             size,
                                             path -> ZkUtils.readNodeData(curatorFramework,
                                                                          appInfoPath(path),
                                                                          mapper,
                                                                          ApplicationInfo.class));
        }
        catch (KeeperException.NoNodeException e) {
            return Collections.emptyList();
        }
    }

    @Override
    public Optional<ApplicationInfo> application(String appId) {
        return Optional.ofNullable(ZkUtils.readNodeData(curatorFramework,
                                                        appInfoPath(appId),
                                                        mapper,
                                                        ApplicationInfo.class));
    }

    @Override
    public boolean updateApplicationState(
            String appId, ApplicationInfo applicationInfo) {
        val res = ZkUtils.setNodeData(curatorFramework, appInfoPath(appId), mapper, applicationInfo);
        appInstances.computeIfAbsent(appId, id -> new HashMap<>());
        return res;
    }


    @Override
    public boolean deleteApplicationState(String appId) {
        val status = ZkUtils.deleteNode(curatorFramework, appInfoPath(appId));
        if (status) {
            appInstances.remove(appId);
            oldInstances.remove(appId);
        }
        return status;
    }

    @Override
    public List<InstanceInfo> activeInstances(String appId, int start, int size) {
        //TODO:: THIS IS NOT PERFORMANT IN TERMS OF MEMORY
        if (!appInstances.containsKey(appId)) {
            return Collections.emptyList();
        }
        return sublist(List.copyOf(sortedInstances(appId, appInstances)), start, size);
    }

    @Override
    public List<InstanceInfo> oldInstances(String appId, int start, int size) {
        //TODO:: THIS IS NOT PERFORMANT IN TERMS OF MEMORY
        if (!oldInstances.containsKey(appId)) {
            return Collections.emptyList();
        }
        return sublist(List.copyOf(sortedInstances(appId, oldInstances)), start, size);
    }

    @Override
    public Optional<InstanceInfo> instance(String appId, String instanceId) {
        if (!appInstances.containsKey(appId)) {
            return Optional.empty();
        }
        return Optional.ofNullable(appInstances.get(appId).getOrDefault(instanceId, oldInstance(appId, instanceId)));
    }

    @Override
    public boolean updateInstanceState(String appId, String instanceId, InstanceInfo instanceInfo) {
        val info = appInstances.compute(appId,
                                        (id, value) -> {
                                            val currState = instanceInfo.getState();
                                            log.trace("State update: {}/{}: {}", appId, instanceId, currState);
                                            val newValue = null != value
                                                           ? value
                                                           : new HashMap<String, InstanceInfo>();
                                            newValue.compute(instanceId, (iid, oldValue) -> {
                                                if (null == oldValue) {
                                                    return instanceInfo;
                                                }
                                                if (DEAD_STATES.contains(currState)) {
                                                    //Remove useless instances for working set
                                                    recordInstance(oldInstances, appId, instanceId, instanceInfo);
                                                    return null;
                                                }
                                                else {
                                                    //If this instance was present in the unknown set, remove it
                                                    if(oldInstances.containsKey(appId)) {
                                                        val oldInfo = oldInstances.get(appId).get(instanceId);
                                                        if(null != oldInfo  && oldInfo.getState() == InstanceState.UNKNOWN) {
                                                            oldInstances.get(appId).remove(instanceId);
                                                        }
                                                    }
                                                }
                                                return instanceInfo;
                                            });
                                            return newValue;
                                        });
        return info.containsKey(instanceId);
    }

    @Override
    public boolean deleteInstanceState(String appId, String instanceId) {
        val info = appInstances.computeIfPresent(appId, (id, value) -> {
            val oldValue = value.remove(instanceId);
            if(null != oldValue) {
                val lostInstance = new InstanceInfo(oldValue.getAppId(),
                                              oldValue.getInstanceId(),
                                              oldValue.getExecutorId(),
                                              oldValue.getLocalInfo(),
                                              oldValue.getResources(),
                                              InstanceState.UNKNOWN,
                                              oldValue.getMetadata(),
                                              oldValue.getCreated(),
                                              new Date());
                recordInstance(oldInstances, appId, instanceId, lostInstance);
            }
            return value;
        });
        return null == info || !info.containsKey(instanceId);
    }

    private String appInfoPath(String appId) {
        return APPLICATION_STATE_PATH + "/" + appId;
    }

    public InstanceInfo oldInstance(String appId, String instanceId) {
        if (!oldInstances.containsKey(appId)) {
            return null;
        }
        return oldInstances.get(appId).get(instanceId);
    }


    private void recordInstance(
            Map<String, Map<String, InstanceInfo>> instanceMap,
            String appId,
            String instanceId,
            InstanceInfo instanceInfo) {
        instanceMap.compute(appId, (aid, oldMap) -> {
            val instances = null != oldMap
                            ? oldMap
                            : new LinkedHashMap<String, InstanceInfo>() {
                                @Override
                                protected boolean removeEldestEntry(Map.Entry<String, InstanceInfo> eldest) {
                                    return this.size() > 10;
                                }
                            };
            instances.put(instanceId, instanceInfo);
            return instances;
        });
    }

    private List<InstanceInfo> sortedInstances(String appId, Map<String, Map<String, InstanceInfo>> appInstances) {
        return appInstances.get(appId)
                .values()
                .stream()
                .sorted(Comparator.comparing(InstanceInfo::getUpdated).reversed())
                .collect(Collectors.toUnmodifiableList());
    }
}
