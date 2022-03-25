package com.phonepe.drove.controller.statedb;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.phonepe.drove.models.instance.InstanceInfo;
import com.phonepe.drove.models.instance.InstanceState;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.apache.curator.framework.CuratorFramework;

import javax.inject.Inject;
import java.time.Duration;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

import static com.phonepe.drove.common.zookeeper.ZkUtils.*;
import static com.phonepe.drove.models.instance.InstanceState.ACTIVE_STATES;
import static com.phonepe.drove.models.instance.InstanceState.LOST;

/**
 *
 */
@Slf4j
public class ZkInstanceInfoDB implements InstanceInfoDB {
    private static final Duration MAX_ACCEPTABLE_UPDATE_INTERVAL = Duration.ofMinutes(1);


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
    public List<InstanceInfo> activeInstances(String appId, Set<InstanceState> validStates, int start, int size) {
        val validUpdateDate = new Date(new Date().getTime() - MAX_ACCEPTABLE_UPDATE_INTERVAL.toMillis());
        return listInstances(appId,
                             start,
                             size,
                             instanceInfo -> validStates.contains(instanceInfo.getState())
                                     && instanceInfo.getUpdated().after(validUpdateDate));
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

    @Override
    @SneakyThrows
    public long markStaleInstances(String appId) {
        val validUpdateDate = new Date(new Date().getTime() - MAX_ACCEPTABLE_UPDATE_INTERVAL.toMillis());
        //Find all instances in active states
        val instances = listInstances(appId,
                                      0,
                                      Integer.MAX_VALUE,
                                      instanceInfo -> ACTIVE_STATES.contains(instanceInfo.getState()));
        //Find all nodes that have not been updated in stipulated time and move them to unknown state
        return instances.stream()
                .filter(instanceInfo -> instanceInfo.getUpdated().before(validUpdateDate))
                .filter(instanceInfo -> {
                    log.warn("Found stale instance {}/{}", appId, instanceInfo.getInstanceId());
                    return updateInstanceState(appId,
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
                })
                .count();
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
