package com.phonepe.drove.controller.statedb;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.phonepe.drove.models.taskinstance.TaskInstanceInfo;
import com.phonepe.drove.models.taskinstance.TaskInstanceState;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.apache.curator.framework.CuratorFramework;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static com.phonepe.drove.common.zookeeper.ZkUtils.*;
import static com.phonepe.drove.models.instance.InstanceState.ACTIVE_STATES;

/**
 *
 */
@Slf4j
@Singleton
public class ZkTaskDB implements TaskDB {
    private static final String TASK_STATE_PATH = "/tasks";

    private final CuratorFramework curatorFramework;
    private final ObjectMapper mapper;

    @Inject
    public ZkTaskDB(CuratorFramework curatorFramework, ObjectMapper mapper) {
        this.curatorFramework = curatorFramework;
        this.mapper = mapper;
    }

    @Override
    public Map<String, List<TaskInstanceInfo>> tasks(
            Collection<String> sourceAppIds,
            Set<TaskInstanceState> validStates,
            boolean skipStaleCheck) {
        val validUpdateDate = new Date(System.currentTimeMillis() - MAX_ACCEPTABLE_UPDATE_INTERVAL.toMillis());
        return sourceAppIds.stream()
                .flatMap(appId -> listInstances(appId,
                                                0,
                                                Integer.MAX_VALUE,
                                                instanceInfo -> validStates.contains(instanceInfo.getState())
                                                        && (skipStaleCheck || instanceInfo.getUpdated().after(
                                                        validUpdateDate))).stream())
                .collect(Collectors.groupingBy(TaskInstanceInfo::getSourceAppName, Collectors.toUnmodifiableList()));
    }

    @Override
    public Optional<TaskInstanceInfo> task(String sourceAppName, String taskId) {
        return Optional.ofNullable(readNodeData(curatorFramework,
                                                instancePath(sourceAppName, taskId),
                                                mapper,
                                                TaskInstanceInfo.class));
    }

    @Override
    public boolean updateTask(String sourceAppName, String taskId, TaskInstanceInfo instanceInfo) {
        return setNodeData(curatorFramework,
                           instancePath(sourceAppName, taskId),
                           mapper,
                           instanceInfo);
    }

    @Override
    public boolean deleteTask(String sourceAppName, String taskId) {
        return deleteNode(curatorFramework, instancePath(sourceAppName, taskId));
    }

    @Override
    public long markStaleTask(String sourceAppName) {
        val validUpdateDate = new Date(new Date().getTime() - MAX_ACCEPTABLE_UPDATE_INTERVAL.toMillis());
        //Find all instances in active states that have not been updated in stipulated time and move them to unknown
        // state
        val instances = listInstances(sourceAppName,
                                      0,
                                      Integer.MAX_VALUE,
                                      instanceInfo -> ACTIVE_STATES.contains(instanceInfo.getState())
                                              && instanceInfo.getUpdated().before(validUpdateDate));
        instances.forEach(instanceInfo -> {
            log.warn("Found stale task instance {}/{}. Current state: {} Last updated at: {}",
                     sourceAppName, instanceInfo.getTaskId(), instanceInfo.getState(), instanceInfo.getUpdated());
            updateTask(sourceAppName,
                       instanceInfo.getTaskId(),
                       new TaskInstanceInfo(instanceInfo.getSourceAppName(),
                                            instanceInfo.getTaskId(),
                                            instanceInfo.getInstanceId(),
                                            instanceInfo.getExecutorId(),
                                            instanceInfo.getHostname(),
                                            instanceInfo.getResources(),
                                            TaskInstanceState.LOST,
                                            instanceInfo.getMetadata(),
                                            "Instance lost",
                                            instanceInfo.getCreated(),
                                            new Date()));
        });
        return instances.size();
    }

    @SneakyThrows
    private List<TaskInstanceInfo> listInstances(
            String appId,
            int start,
            int size,
            Predicate<TaskInstanceInfo> filter) {
        val parentPath = instancePath(appId);
        return readChildrenNodes(curatorFramework,
                                 parentPath, start, size,
                                 instanceId -> readNodeData(curatorFramework,
                                                            instanceInfoPath(parentPath, instanceId),
                                                            mapper,
                                                            TaskInstanceInfo.class,
                                                            filter));
    }

    private static String instancePath(final String sourceAppId) {
        return TASK_STATE_PATH + "/" + sourceAppId;
    }

    private static String instanceInfoPath(final String parent, final String instanceId) {
        return parent + "/" + instanceId;
    }

    private String instancePath(String appId, String instanceId) {
        return instanceInfoPath(instancePath(appId), instanceId);
    }
}
