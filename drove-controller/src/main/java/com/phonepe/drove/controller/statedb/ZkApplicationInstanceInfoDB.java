/*
 *  Copyright (c) 2024 Original Author(s), PhonePe India Pvt. Ltd.
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
import com.phonepe.drove.models.instance.InstanceInfo;
import com.phonepe.drove.models.instance.InstanceState;
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
import static com.phonepe.drove.models.instance.InstanceState.ACTIVE_STATES;
import static com.phonepe.drove.models.instance.InstanceState.LOST;

/**
 *
 */
@Slf4j
@Singleton
public class ZkApplicationInstanceInfoDB implements ApplicationInstanceInfoDB {

    @SuppressWarnings("java:S1075")
    private static final String INSTANCE_STATE_PATH = "/instances";

    private final CuratorFramework curatorFramework;
    private final ObjectMapper mapper;

    @Inject
    public ZkApplicationInstanceInfoDB(CuratorFramework curatorFramework, ObjectMapper mapper) {
        this.curatorFramework = curatorFramework;
        this.mapper = mapper;
    }

    @Override
    @MonitoredFunction
    public Map<String, List<InstanceInfo>> instances(
            Collection<String> appIds,
            Set<InstanceState> validStates,
            boolean skipStaleCheck) {
        val validUpdateDate = new Date(System.currentTimeMillis() - MAX_ACCEPTABLE_UPDATE_INTERVAL.toMillis());
        return appIds.stream()
                .flatMap(appId -> listInstances(appId,
                                                0,
                                                Integer.MAX_VALUE,
                                                instanceInfo -> validStates.contains(instanceInfo.getState())
                                                        && (skipStaleCheck || instanceInfo.getUpdated().after(validUpdateDate))).stream())
                .collect(Collectors.groupingBy(InstanceInfo::getAppId, Collectors.toUnmodifiableList()));
    }

    @Override
    @MonitoredFunction
    public Optional<InstanceInfo> instance(String appId, String instanceId) {
        return Optional.ofNullable(readNodeData(curatorFramework,
                                                instancePath(appId, instanceId),
                                                mapper,
                                                InstanceInfo.class));
    }

    @Override
    @MonitoredFunction
    public boolean updateInstanceState(
            String appId, String instanceId, InstanceInfo instanceInfo) {
        return setNodeData(curatorFramework,
                           instancePath(appId, instanceId),
                           mapper,
                           instanceInfo);
    }

    @Override
    @MonitoredFunction
    public boolean deleteInstanceState(String appId, String instanceId) {
        return deleteNode(curatorFramework, instancePath(appId, instanceId));
    }

    @Override
    @MonitoredFunction
    public boolean deleteAllInstancesForApp(String appId) {
        return deleteNode(curatorFramework, instancePath(appId));
    }

    @Override
    @SneakyThrows
    @MonitoredFunction
    public long markStaleInstances(String appId) {
        val validUpdateDate = new Date(new Date().getTime() - MAX_ACCEPTABLE_UPDATE_INTERVAL.toMillis());
        //Find all instances in active states that have not been updated in stipulated time and move them to unknown state
        val instances = listInstances(appId,
                                      0,
                                      Integer.MAX_VALUE,
                                      instanceInfo -> ACTIVE_STATES.contains(instanceInfo.getState())
                                              && instanceInfo.getUpdated().before(validUpdateDate));
        instances.forEach(instanceInfo -> {
                    log.warn("Found stale instance {}/{}. Current state: {} Last updated at: {}",
                             appId, instanceInfo.getInstanceId(), instanceInfo.getState(), instanceInfo.getUpdated());
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

    private static String instancePath(final String applicationId) {
        return INSTANCE_STATE_PATH + "/" + applicationId;
    }

    private static String instanceInfoPath(final String parent, final String instanceId) {
        return parent + "/" + instanceId;
    }

    private String instancePath(String appId, String instanceId) {
        return instanceInfoPath(instancePath(appId), instanceId);
    }

    @SneakyThrows
    private List<InstanceInfo> listInstances(
            String appId,
            int start,
            int size,
            Predicate<InstanceInfo> filter) {
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
