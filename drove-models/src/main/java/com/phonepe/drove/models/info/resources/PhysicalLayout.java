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

package com.phonepe.drove.models.info.resources;

import com.phonepe.drove.models.info.GPUInfo;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

import java.util.Map;
import java.util.Set;

/**
 * Physical layout of the host
 */
@Value
@Jacksonized
@AllArgsConstructor
@Builder
@Schema(description = "Physical layout of the executor host describing NUMA topology with CPU cores and memory per NUMA node")
public class PhysicalLayout {
    public static final double DEFAULT_CPU_SCALING_FACTOR = 1.0;
    public static final double DEFAULT_MEMORY_SCALING_FACTOR = 1.0;

    @Schema(description = "Map of NUMA node ID to set of CPU core IDs on that node", example = "{0: [0,1,2,3], 1: [4,5,6,7]}")
    Map<Integer, Set<Integer>> cores;

    @Schema(description = "Map of NUMA node ID to available memory in bytes on that node", example = "{0: 17179869184, 1: 17179869184}")
    Map<Integer, Long> memory;

    @Schema(description = "Map of GPU ID to GPU info for GPUs available in the host")
    @Builder.Default
    Map<String, GPUInfo> gpus = Map.of();

    @Schema(description = "CPU Performance Scaling Factor for the host", example = "1.0")
    @Builder.Default
    double cpuScalingFactor = DEFAULT_CPU_SCALING_FACTOR;

    @Schema(description = "Memory Performance Scaling Factor for the host", example = "1.0")
    @Builder.Default
    double memoryScalingFactor = DEFAULT_MEMORY_SCALING_FACTOR;

}
