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
import com.phonepe.drove.models.instance.LocalServiceInstanceState;
import com.phonepe.drove.models.localservice.LocalServiceInfo;
import com.phonepe.drove.models.localservice.LocalServiceInstanceInfo;
import io.appform.functionmetrics.MonitoredFunction;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.apache.curator.framework.CuratorFramework;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

import static com.phonepe.drove.common.zookeeper.ZkUtils.*;

/**
 *
 */
@Singleton
@Slf4j
@SuppressWarnings("java:S1075")
public class ZKLocalServiceStateDB implements LocalServiceStateDB {
    private static final String SERVICE_STATE_PATH = "/localservices";
    private static final String SERVICE_INSTANCES_PATH = "/localserviceinstances";

    private final CuratorFramework curatorFramework;
    private final ObjectMapper mapper;

    @Inject
    public ZKLocalServiceStateDB(CuratorFramework curatorFramework, ObjectMapper mapper) {
        this.curatorFramework = curatorFramework;
        this.mapper = mapper;
    }

    @Override
    @MonitoredFunction
    public Optional<LocalServiceInfo> service(final String serviceId) {
        return Optional.ofNullable(readNodeData(curatorFramework,
                                                servicePath(serviceId),
                                                mapper,
                                                LocalServiceInfo.class));
    }

    @Override
    @MonitoredFunction
    public List<LocalServiceInfo> services(int start, int size) {
        try {
            return readChildrenNodes(curatorFramework,
                                     SERVICE_STATE_PATH,
                                     start,
                                     size,
                                     path -> readNodeData(curatorFramework,
                                                          servicePath(path),
                                                          mapper,
                                                          LocalServiceInfo.class));
        }
        catch (Exception e) {
            log.error("Error reading application list: " + e.getMessage(), e);
            return List.of();
        }
    }

    @Override
    @MonitoredFunction
    public boolean updateService(String serviceId, LocalServiceInfo info) {
        return setNodeData(curatorFramework, servicePath(serviceId), mapper, info.withUpdated(new Date()));
    }

    @Override
    public boolean removeService(String serviceId) {
        return deleteNode(curatorFramework, servicePath(serviceId));
    }

    @Override
    public Optional<LocalServiceInstanceInfo> instance(String serviceId, String instanceId) {
        return Optional.ofNullable(readNodeData(curatorFramework,
                                                instancePath(serviceId, instanceId),
                                                mapper,
                                                LocalServiceInstanceInfo.class));
    }

    @Override
    public List<LocalServiceInstanceInfo> instances(
            String serviceId, Set<LocalServiceInstanceState> states,
            boolean skipStaleCheck) {
        val validUpdateDate = new Date(System.currentTimeMillis() - MAX_ACCEPTABLE_UPDATE_INTERVAL.toMillis());
        return listInstances(serviceId,
                             0,
                             Integer.MAX_VALUE,
                             instanceInfo -> states.contains(instanceInfo.getState())
                                     && (skipStaleCheck || instanceInfo.getUpdated().after(validUpdateDate)));
    }

    @Override
    public boolean deleteInstanceState(String serviceId, String instanceId) {
        return deleteNode(curatorFramework, instancePath(serviceId, instanceId));
    }

    @Override
    public boolean deleteAllInstancesForService(String serviceId) {
        return deleteNode(curatorFramework, instancePath(serviceId));
    }

    @Override
    public boolean updateInstanceState(String serviceId, String instanceId, LocalServiceInstanceInfo instanceInfo) {
        return setNodeData(curatorFramework,
                           instancePath(serviceId, instanceId),
                           mapper,
                           instanceInfo);
    }

    @Override
    @SneakyThrows
    @MonitoredFunction
    public long markStaleInstances(String serviceId) {
        val validUpdateDate = new Date(new Date().getTime() - MAX_ACCEPTABLE_UPDATE_INTERVAL.toMillis());
        //Find all instances in active states that have not been updated in stipulated time and move them to unknown state
        val instances = listInstances(serviceId,
                                      0,
                                      Integer.MAX_VALUE,
                                      instanceInfo -> LocalServiceInstanceState.ACTIVE_STATES.contains(instanceInfo.getState())
                                              && instanceInfo.getUpdated().before(validUpdateDate));
        instances.forEach(instanceInfo -> {
            log.warn("Found stale service instance {}/{}. Current state: {} Last updated at: {}",
                     serviceId, instanceInfo.getInstanceId(), instanceInfo.getState(), instanceInfo.getUpdated());
            updateInstanceState(serviceId,
                                instanceInfo.getInstanceId(),
                                instanceInfo.withState(LocalServiceInstanceState.LOST)
                                        .withErrorMessage("Instance lost")
                                        .withUpdated(new Date()));
        });
        return instances.size();
    }

    @SneakyThrows
    private List<LocalServiceInstanceInfo> listInstances(
            String staleCheck,
            int start,
            int size,
            Predicate<LocalServiceInstanceInfo> filter) {
        val parentPath = instancePath(staleCheck);
        return readChildrenNodes(curatorFramework,
                                 parentPath, start, size,
                                 instanceId -> readNodeData(curatorFramework,
                                                            instanceInfoPath(parentPath, instanceId),
                                                            mapper,
                                                            LocalServiceInstanceInfo.class,
                                                            filter));
    }

    private static String servicePath(String appId) {
        return SERVICE_STATE_PATH + "/" + appId;
    }

    private static String instancePath(final String serviceId) {
        return SERVICE_INSTANCES_PATH + "/" + serviceId;
    }

    private static String instanceInfoPath(final String parent, final String instanceId) {
        return parent + "/" + instanceId;
    }

    private String instancePath(String appId, String instanceId) {
        return instanceInfoPath(instancePath(appId), instanceId);
    }
}
