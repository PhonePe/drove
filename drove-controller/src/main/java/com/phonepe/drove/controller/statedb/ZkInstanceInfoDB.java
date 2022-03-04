package com.phonepe.drove.controller.statedb;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.phonepe.drove.models.instance.InstanceInfo;
import com.phonepe.drove.models.instance.InstanceState;
import lombok.SneakyThrows;
import lombok.val;
import org.apache.curator.framework.CuratorFramework;

import javax.inject.Inject;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

import static com.phonepe.drove.common.zookeeper.ZkUtils.*;
import static com.phonepe.drove.models.instance.InstanceState.*;

/**
 *
 */
public class ZkInstanceInfoDB implements InstanceInfoDB {
    private static final Set<InstanceState> ACTIVE_STATES = EnumSet.of(PENDING,
                                                                       PROVISIONING,
                                                                       STARTING,
                                                                       UNREADY,
                                                                       HEALTHY,
                                                                       UNHEALTHY,
                                                                       DEPROVISIONING,
                                                                       STOPPING);
    private static final String INSTANCE_STATE_PATH = "/instances";

    private final CuratorFramework curatorFramework;
    private final ObjectMapper mapper;

    @Inject
    public ZkInstanceInfoDB(CuratorFramework curatorFramework, ObjectMapper mapper) {
        this.curatorFramework = curatorFramework;
        this.mapper = mapper;
    }

    @Override
    @SneakyThrows
    public List<InstanceInfo> activeInstances(String appId, int start, int size) {
        return listInstances(appId, start, size, instanceInfo -> ACTIVE_STATES.contains(instanceInfo.getState()));
    }

    @Override
    @SneakyThrows
    public List<InstanceInfo> oldInstances(String appId, int start, int size) {
        return listInstances(appId, start, size, instanceInfo -> !ACTIVE_STATES.contains(instanceInfo.getState()));
    }

    @Override
    public Optional<InstanceInfo> instance(String appId, String instanceId) {
        return Optional.ofNullable(readNodeData(curatorFramework,
                                                instancePath(appId, instanceId),
                                                mapper,
                                                InstanceInfo.class));
    }

    @Override
    public boolean updateInstanceState(
            String appId, String instanceId, InstanceInfo instanceInfo) {
        return setNodeData(curatorFramework,
                                   instancePath(appId, instanceId),
                                   mapper,
                                   instanceInfo);
    }

    @Override
    public boolean deleteInstanceState(String appId, String instanceId) {
        return deleteNode(curatorFramework, instancePath(appId, instanceId));
    }

    @Override
    @SneakyThrows
    public boolean deleteAllInstancesForApp(String appId) {
        return deleteNode(curatorFramework, instancePath(appId));
    }

    private static String instancePath(final String applicationId) {
        return INSTANCE_STATE_PATH + "/" + applicationId;
    }

    private static String instanceInfoPath(final String parent, final String instanceId) {
        return parent + "/" + instanceId;
    }

    private String instancePath(String appId, String instanceId) {
        return instanceInfoPath(instancePath(appId), instanceId);
    }

    private List<InstanceInfo> listInstances(
            String appId,
            int start,
            int size,
            Predicate<InstanceInfo> filter) throws Exception {
        val parentPath = instancePath(appId);
        return readChildrenNodes(curatorFramework,
                                 parentPath, start, size,
                                 instanceId -> readNodeData(curatorFramework,
                                                            instanceInfoPath(parentPath, instanceId),
                                                            mapper,
                                                            InstanceInfo.class,
                                                            filter));
    }
}
