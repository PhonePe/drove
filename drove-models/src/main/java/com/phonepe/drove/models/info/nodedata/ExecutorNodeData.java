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

package com.phonepe.drove.models.info.nodedata;

import com.phonepe.drove.models.info.ExecutorResourceSnapshot;
import com.phonepe.drove.models.instance.InstanceInfo;
import com.phonepe.drove.models.localservice.LocalServiceInstanceInfo;
import com.phonepe.drove.models.taskinstance.TaskInfo;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

import java.util.*;

/**
 * Data about an executor node in the Drove cluster.
 */
@Value
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Schema(description = "Data about an executor node in the Drove cluster, including resource state and running instances")
public class ExecutorNodeData extends NodeData {

    @Schema(description = "Current resource snapshot of the executor (CPU, memory availability)")
    ExecutorResourceSnapshot state;

    @Schema(description = "List of application instances running on this executor")
    List<InstanceInfo> instances;

    @Schema(description = "List of tasks running on this executor")
    List<TaskInfo> tasks;

    @Schema(description = "List of local service instances running on this executor")
    List<LocalServiceInstanceInfo> serviceInstances;

    @Schema(description = "Tags associated with this executor for placement decisions", example = "[\"gpu\", \"high-memory\"]")
    Set<String> tags;

    @Schema(description = "Custom metadata associated with this executor", example = "{\"rack\": \"r1\", \"dc\": \"dc1\"}")
    Map<String, String> metadata;

    @Schema(description = "Current state of the executor node")
    ExecutorState executorState;

    @Jacksonized
    @Builder
    @SuppressWarnings("java:S107")
    public ExecutorNodeData(
            String hostname,
            int port,
            NodeTransportType transportType,
            Date updated,
            ExecutorResourceSnapshot state,
            List<InstanceInfo> instances,
            List<TaskInfo> tasks,
            List<LocalServiceInstanceInfo> serviceInstances,
            Set<String> tags,
            Map<String, String> metadata,
            ExecutorState executorState) {
        super(NodeType.EXECUTOR, hostname, port, transportType, updated);
        this.state = state;
        this.instances = instances;
        this.tasks = tasks;
        this.serviceInstances = serviceInstances;
        this.tags = tags;
        this.metadata = metadata;
        this.executorState = executorState;
    }

    @Override
    public <T> T accept(NodeDataVisitor<T> visitor) {
        return visitor.visit(this);
    }

    public static ExecutorNodeData from(
            final ExecutorNodeData nodeData,
            final ExecutorResourceSnapshot currentState,
            final List<InstanceInfo> instances,
            final List<TaskInfo> taskInstances,
            final List<LocalServiceInstanceInfo> serviceInstances,
            final Set<String> tags,
            final ExecutorState executorState,
            final Map<String, String> metadata) {
        return new ExecutorNodeData(nodeData.getHostname(),
                                    nodeData.getPort(),
                                    nodeData.getTransportType(),
                                    new Date(),
                                    currentState,
                                    instances,
                                    taskInstances,
                                    serviceInstances,
                                    null == tags ? Collections.emptySet() : tags,
                                    Objects.requireNonNullElse(metadata, Map.of()),
                                    executorState
                );

    }
}
