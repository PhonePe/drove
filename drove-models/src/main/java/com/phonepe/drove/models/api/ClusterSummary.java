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

import com.phonepe.drove.models.common.ClusterState;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Value;

/**
 * Summary view of the cluster state
 */
@Value
@Schema(description = "Summary information about the Drove cluster")
public class ClusterSummary {
    @Schema(description = "Hostname of the current cluster leader controller", example = "controller-1.example.com")
    String leader;

    @Schema(description = "Current cluster state")
    ClusterState state;

    @Schema(description = "Total number of executor nodes in the cluster", example = "10")
    int numExecutors;

    @Schema(description = "Total number of registered applications", example = "25")
    int numApplications;

    @Schema(description = "Number of applications with at least one healthy instance", example = "20")
    int numActiveApplications;

    @Schema(description = "Number of currently running tasks", example = "5")
    int numActiveTasks;

    @Schema(description = "Total number of registered local services", example = "8")
    int numLocalServices;

    @Schema(description = "Number of local services with at least one active instance", example = "6")
    int numActiveLocalServices;

    @Schema(description = "Number of unallocated CPU cores across all executors", example = "50")
    int freeCores;

    @Schema(description = "Number of allocated CPU cores across all executors", example = "150")
    int usedCores;

    @Schema(description = "Total CPU cores available in the cluster", example = "200")
    int totalCores;

    @Schema(description = "Unallocated memory in bytes across all executors", example = "107374182400")
    long freeMemory;

    @Schema(description = "Allocated memory in bytes across all executors", example = "322122547200")
    long usedMemory;

    @Schema(description = "Total memory in bytes available in the cluster", example = "429496729600")
    long totalMemory;
}
