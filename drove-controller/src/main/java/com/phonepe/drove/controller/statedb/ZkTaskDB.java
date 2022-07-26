package com.phonepe.drove.controller.statedb;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.phonepe.drove.models.taskinstance.TaskInfo;
import com.phonepe.drove.models.taskinstance.TaskState;
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
import static com.phonepe.drove.models.taskinstance.TaskState.ACTIVE_STATES;

/**
 *
 */
@Slf4j
@Singleton
public class ZkTaskDB extends TaskDB {
    @SuppressWarnings("java:S1075")
    private static final String TASK_STATE_PATH = "/tasks";

    private final CuratorFramework curatorFramework;
    private final ObjectMapper mapper;

    @Inject
    public ZkTaskDB(CuratorFramework curatorFramework, ObjectMapper mapper) {
        this.curatorFramework = curatorFramework;
        this.mapper = mapper;
    }

    @Override
    public Map<String, List<TaskInfo>> tasks(
            Collection<String> sourceAppIds,
            Set<TaskState> validStates,
            boolean skipStaleCheck) {
        val validUpdateDate = new Date(System.currentTimeMillis() - MAX_ACCEPTABLE_UPDATE_INTERVAL.toMillis());
        return sourceAppIds.stream()
                .flatMap(appId -> listTasks(appId,
                                            0,
                                            Integer.MAX_VALUE,
                                                instanceInfo -> validStates.contains(instanceInfo.getState())
                                                        && (skipStaleCheck || instanceInfo.getUpdated().after(
                                                        validUpdateDate))).stream())
                .collect(Collectors.groupingBy(TaskInfo::getSourceAppName, Collectors.toUnmodifiableList()));
    }

    @Override
    public Optional<TaskInfo> task(String sourceAppName, String taskId) {
        return Optional.ofNullable(readNodeData(curatorFramework,
                                                instancePath(sourceAppName, taskId),
                                                mapper,
                                                TaskInfo.class));
    }

    @Override
    protected boolean updateTaskImpl(String sourceAppName, String taskId, TaskInfo instanceInfo) {
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
    public Optional<TaskInfo> checkedCurrentState(String sourceAppName, String taskId) {
        val validUpdateDate = new Date(new Date().getTime() - MAX_ACCEPTABLE_UPDATE_INTERVAL.toMillis());
        val instance = task(sourceAppName, taskId).orElse(null);
        if(null == instance
            || !ACTIVE_STATES.contains(instance.getState())
            || instance.getUpdated().after(validUpdateDate)) {
            return Optional.ofNullable(instance);
        }
        log.warn("Found stale task instance {}/{}. Current state: {} Last updated at: {}",
                 sourceAppName, instance.getTaskId(), instance.getState(), instance.getUpdated());
        val updateStatus = updateTaskImpl(sourceAppName,
                                          taskId,
                                          new TaskInfo(instance.getSourceAppName(),
                                                       instance.getTaskId(),
                                                       instance.getInstanceId(),
                                                       instance.getExecutorId(),
                                                       instance.getHostname(),
                                                       instance.getExecutable(),
                                                       instance.getResources(),
                                                       instance.getVolumes(),
                                                       instance.getLoggingSpec(),
                                                       instance.getEnv(),
                                                       TaskState.LOST,
                                                       instance.getMetadata(),
                                                       "Instance lost",
                                                       instance.getCreated(),
                                                       new Date()));
        log.info("Stale mark status for task {}/{} is {}", sourceAppName, taskId, updateStatus);
        return task(sourceAppName, taskId);
    }

    @SneakyThrows
    private List<TaskInfo> listTasks(
            String appId,
            int start,
            int size,
            Predicate<TaskInfo> filter) {
        val parentPath = instancePath(appId);
        return readChildrenNodes(curatorFramework,
                                 parentPath, start, size,
                                 instanceId -> readNodeData(curatorFramework,
                                                            instanceInfoPath(parentPath, instanceId),
                                                            mapper,
                                                            TaskInfo.class,
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
