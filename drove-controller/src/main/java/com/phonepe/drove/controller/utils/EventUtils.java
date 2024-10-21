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

package com.phonepe.drove.controller.utils;

import com.google.common.base.Joiner;
import com.google.common.base.Strings;
import com.phonepe.drove.common.CommonUtils;
import com.phonepe.drove.models.application.ApplicationInfo;
import com.phonepe.drove.models.application.ApplicationSpec;
import com.phonepe.drove.models.application.ApplicationState;
import com.phonepe.drove.models.application.executable.DockerCoordinates;
import com.phonepe.drove.models.application.placement.PlacementPolicy;
import com.phonepe.drove.models.application.placement.PlacementPolicyType;
import com.phonepe.drove.models.application.requirements.CPURequirement;
import com.phonepe.drove.models.application.requirements.MemoryRequirement;
import com.phonepe.drove.models.application.requirements.ResourceRequirementVisitor;
import com.phonepe.drove.models.events.MapBuilder;
import com.phonepe.drove.models.events.events.datatags.*;
import com.phonepe.drove.models.info.nodedata.ExecutorNodeData;
import com.phonepe.drove.models.instance.InstanceInfo;
import com.phonepe.drove.models.instance.InstancePort;
import com.phonepe.drove.models.localservice.LocalServiceInstanceInfo;
import com.phonepe.drove.models.taskinstance.TaskInfo;
import com.phonepe.drove.statemachine.StateData;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.val;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;


/**
 *
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class EventUtils {
    public static Map<AppEventDataTag, Object> appMetadata(
            final String appId,
            final ApplicationSpec spec,
            StateData<ApplicationState, ApplicationInfo> newState) {
        val metadata = new MapBuilder<AppEventDataTag, Object>()
                .put(AppEventDataTag.APP_ID, appId)
                .put(AppEventDataTag.CURRENT_STATE, newState.getState())
                .put(AppEventDataTag.APP_NAME, spec.getName())
                .put(AppEventDataTag.APP_VERSION, spec.getVersion())
                .put(AppEventDataTag.EXECUTABLE,
                     spec.getExecutable().accept(DockerCoordinates::getUrl))

                .put(AppEventDataTag.PLACEMENT_POLICY,
                     Optional.ofNullable(spec.getPlacementPolicy())
                             .map(PlacementPolicy::getType)
                             .orElse(PlacementPolicyType.ANY))
                .put(AppEventDataTag.EXECUTABLE,
                     spec.getExecutable().accept(DockerCoordinates::getUrl))
                .put(AppEventDataTag.CURRENT_INSTANCES,
                     Optional.ofNullable(newState.getData())
                             .map(ApplicationInfo::getInstances)
                             .orElse(0L));
        spec.getResources()
                .forEach(resourceRequirement -> resourceRequirement.accept(new ResourceRequirementVisitor<Void>() {
                    @Override
                    public Void visit(CPURequirement cpuRequirement) {
                        metadata.put(AppEventDataTag.CPU_COUNT, cpuRequirement.getCount());
                        return null;
                    }

                    @Override
                    public Void visit(MemoryRequirement memoryRequirement) {
                        metadata.put(AppEventDataTag.MEMORY, memoryRequirement.getSizeInMB());
                        return null;
                    }
                }));
        Optional.ofNullable(spec.getExposedPorts())
                .ifPresent(ports -> metadata.put(AppEventDataTag.PORTS, Joiner.on(",")
                        .join(ports.stream()
                                      .map(portSpec -> portSpec.getName()
                                              + ":" + portSpec.getPort()
                                              + ":" + portSpec.getType().name().toLowerCase())
                                      .toList())));
        Optional.ofNullable(spec.getExposureSpec())
                .ifPresent(exposureSpec -> metadata.put(AppEventDataTag.VHOST, exposureSpec.getVhost()));

        return metadata.build();
    }

    public static Map<AppInstanceEventDataTag, Object> instanceMetadata(final InstanceInfo instanceInfo) {
        val metadata = new MapBuilder<AppInstanceEventDataTag, Object>()
                .put(AppInstanceEventDataTag.APP_ID, instanceInfo.getAppId())
                .put(AppInstanceEventDataTag.APP_NAME, instanceInfo.getAppName())
                .put(AppInstanceEventDataTag.INSTANCE_ID, instanceInfo.getInstanceId())
                .put(AppInstanceEventDataTag.EXECUTOR_ID, instanceInfo.getExecutorId())
                .put(AppInstanceEventDataTag.CREATED, instanceInfo.getCreated().getTime())
                .put(AppInstanceEventDataTag.CURRENT_STATE, instanceInfo.getState());
        Optional.ofNullable(instanceInfo.getLocalInfo())
                .ifPresent(info -> metadata.put(AppInstanceEventDataTag.EXECUTOR_HOST, info.getHostname())
                        .put(AppInstanceEventDataTag.PORTS, Joiner.on(",")
                                .join(Objects.requireNonNullElse(instanceInfo.getLocalInfo().getPorts(),
                                                                 Map.<String, InstancePort>of())
                                              .entrySet()
                                              .stream()
                                              .map(entry -> entry.getKey()
                                                      + ":" + entry.getValue().getHostPort()
                                                      + ":" + entry.getValue().getPortType().name().toLowerCase())
                                              .toList())));
        if (!Strings.isNullOrEmpty(instanceInfo.getErrorMessage())) {
            metadata.put(AppInstanceEventDataTag.ERROR, instanceInfo.getErrorMessage());
        }
        return metadata.build();
    }

    public static Map<TaskInstanceEventDataTag, Object> instanceMetadata(final TaskInfo instanceInfo) {
        val metadata = new MapBuilder<TaskInstanceEventDataTag, Object>()
                .put(TaskInstanceEventDataTag.APP_NAME, instanceInfo.getSourceAppName())
                .put(TaskInstanceEventDataTag.TASK_ID, instanceInfo.getTaskId())
                .put(TaskInstanceEventDataTag.INSTANCE_ID, instanceInfo.getInstanceId())
                .put(TaskInstanceEventDataTag.EXECUTOR_ID, instanceInfo.getExecutorId())
                .put(TaskInstanceEventDataTag.EXECUTOR_HOST, instanceInfo.getHostname())
                .put(TaskInstanceEventDataTag.EXECUTABLE,
                     instanceInfo.getExecutable().accept(DockerCoordinates::getUrl))
                .put(TaskInstanceEventDataTag.CREATED, instanceInfo.getCreated().getTime())
                .put(TaskInstanceEventDataTag.CURRENT_STATE, instanceInfo.getState());

        if (!Strings.isNullOrEmpty(instanceInfo.getErrorMessage())) {
            metadata.put(TaskInstanceEventDataTag.ERROR, instanceInfo.getErrorMessage());
        }
        Optional.ofNullable(instanceInfo.getTaskResult())
                .ifPresent(result -> metadata.put(TaskInstanceEventDataTag.RESULT_STATUS, result.getStatus())
                        .put(TaskInstanceEventDataTag.RESULT_EXIT_CODE, result.getExitCode()));

        return metadata.build();
    }

    public static Map<LocalServiceInstanceEventDataTag, Object> instanceMetadata(final LocalServiceInstanceInfo instanceInfo) {
        val metadata = new MapBuilder<LocalServiceInstanceEventDataTag, Object>()
                .put(LocalServiceInstanceEventDataTag.SERVICE_ID, instanceInfo.getServiceId())
                .put(LocalServiceInstanceEventDataTag.SERVICE_NAME, instanceInfo.getServiceName())
                .put(LocalServiceInstanceEventDataTag.INSTANCE_ID, instanceInfo.getInstanceId())
                .put(LocalServiceInstanceEventDataTag.EXECUTOR_ID, instanceInfo.getExecutorId())
                .put(LocalServiceInstanceEventDataTag.CREATED, instanceInfo.getCreated().getTime())
                .put(LocalServiceInstanceEventDataTag.CURRENT_STATE, instanceInfo.getState());
        Optional.ofNullable(instanceInfo.getLocalInfo())
                .ifPresent(info -> metadata.put(LocalServiceInstanceEventDataTag.EXECUTOR_HOST, info.getHostname())
                        .put(LocalServiceInstanceEventDataTag.PORTS, Joiner.on(",")
                                .join(Objects.requireNonNullElse(instanceInfo.getLocalInfo().getPorts(),
                                                                 Map.<String, InstancePort>of())
                                              .entrySet()
                                              .stream()
                                              .map(entry -> entry.getKey()
                                                      + ":" + entry.getValue().getHostPort()
                                                      + ":" + entry.getValue().getPortType().name().toLowerCase())
                                              .toList())));
        if (!Strings.isNullOrEmpty(instanceInfo.getErrorMessage())) {
            metadata.put(LocalServiceInstanceEventDataTag.ERROR, instanceInfo.getErrorMessage());
        }
        return metadata.build();
    }
    
    public static Map<ExecutorEventDataTag, Object> executorMetadata(final String executorId) {
        return new MapBuilder<ExecutorEventDataTag, Object>()
                .put(ExecutorEventDataTag.EXECUTOR_ID, executorId)
                .build();
    }

    public static Map<ExecutorEventDataTag, Object> executorMetadata(final ExecutorNodeData nodeData) {
        return new MapBuilder<ExecutorEventDataTag, Object>()
                .put(ExecutorEventDataTag.EXECUTOR_ID, nodeData.getState().getExecutorId())
                .put(ExecutorEventDataTag.HOSTNAME, nodeData.getHostname())
                .put(ExecutorEventDataTag.PORT, nodeData.getPort())
                .build();
    }

    public static Map<ClusterEventDataTag, Object> controllerMetadata() {
        return new MapBuilder<ClusterEventDataTag, Object>()
                .put(ClusterEventDataTag.HOSTNAME, CommonUtils.hostname())
                .build();
    }
}
