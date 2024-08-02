/*
 *  Copyright (c) 2022 Original Author(s), PhonePe India Pvt. Ltd.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package com.phonepe.drove.controller.statedb;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.phonepe.drove.common.zookeeper.ZkUtils;
import com.phonepe.drove.models.taskinstance.TaskInfo;
import com.phonepe.drove.models.taskinstance.TaskState;
import io.appform.functionmetrics.MonitoredFunction;
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
    @MonitoredFunction
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
    @MonitoredFunction
    @SneakyThrows
    public void cleanupTasks(Predicate<TaskInfo> filter) {
        val appIds = ZkUtils.readChildrenNodes(curatorFramework,
                                               TASK_STATE_PATH,
                                               0,
                                               Integer.MAX_VALUE,
                                               path -> TASK_STATE_PATH + "/" + path);
        log.info("The following task parent app paths have been identified: {}", appIds);
        appIds.forEach(appPath -> {
            try {
                val children = ZkUtils.readChildrenNodes(curatorFramework,
                                                         appPath,
                                                         0,
                                                         Integer.MAX_VALUE,
                                                         path -> ZkUtils.readNodeData(curatorFramework,
                                                                                      appPath + "/" + path,
                                                                                      mapper,
                                                                                      TaskInfo.class));
                children.stream()
                        .filter(filter)
                        .forEach(node -> deleteTask(node.getSourceAppName(), node.getTaskId()));
            }
            catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Override
    @MonitoredFunction
    public Optional<TaskInfo> task(String sourceAppName, String taskId) {
        return Optional.ofNullable(readNodeData(curatorFramework,
                                                instancePath(sourceAppName, taskId),
                                                mapper,
                                                TaskInfo.class));
    }

    @Override
    @MonitoredFunction(method = "update")
    protected boolean updateTaskImpl(String sourceAppName, String taskId, TaskInfo instanceInfo) {
        return setNodeData(curatorFramework,
                           instancePath(sourceAppName, taskId),
                           mapper,
                           instanceInfo);
    }

    @Override
    @MonitoredFunction
    public boolean deleteTask(String sourceAppName, String taskId) {
        return deleteNode(curatorFramework, instancePath(sourceAppName, taskId));
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
