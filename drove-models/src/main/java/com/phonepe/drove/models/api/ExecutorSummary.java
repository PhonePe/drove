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

package com.phonepe.drove.models.api;

import com.phonepe.drove.models.info.nodedata.ExecutorState;
import com.phonepe.drove.models.info.nodedata.NodeTransportType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Value;

import java.util.Set;

/**
 * Summary view of an executor node
 */
@Value
@Schema(description = "Summary information about an executor node in the cluster")
public class ExecutorSummary {
    @Schema(description = "Unique executor identifier", example = "executor-1-abcd1234")
    String executorId;

    @Schema(description = "Hostname of the executor node", example = "executor-1.example.com")
    String hostname;

    @Schema(description = "Port on which the executor is listening", example = "3000")
    int port;

    @Schema(description = "Transport protocol used by the executor")
    NodeTransportType transportType;

    @Schema(description = "Number of unallocated CPU cores on this executor", example = "8")
    int freeCores;

    @Schema(description = "Number of allocated CPU cores on this executor", example = "24")
    int usedCores;

    @Schema(description = "Unallocated memory in bytes on this executor", example = "17179869184")
    long freeMemory;

    @Schema(description = "Allocated memory in bytes on this executor", example = "51539607552")
    long usedMemory;

    @Schema(description = "Tags assigned to this executor for placement policies")
    Set<String> tags;

    @Schema(description = "Current state of the executor")
    ExecutorState state;
}
