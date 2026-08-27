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
 *  See the License for the specific language and
 *  limitations under the License.
 */

package com.phonepe.drove.models.api;

import com.phonepe.drove.models.application.requirements.GpuVendor;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Value;

/**
 * Represents a single GPU resource in the cluster
 */
@Value
@Schema(description = "Information about a single GPU device in the cluster")
public class GpuResourceInfo {

    public enum GpuState {
        FREE,
        IN_USE
    }

    @Schema(description = "Executor ID hosting this GPU", example = "executor-1-abcd1234")
    String executorId;

    @Schema(description = "Hostname of the executor node", example = "executor-1.example.com")
    String hostname;

    @Schema(description = "Unique GPU device identifier", example = "GPU-abcdef12-3456-7890")
    String gpuId;

    @Schema(description = "GPU vendor")
    GpuVendor vendor;

    @Schema(description = "GPU model name", example = "A100-SXM4-40GB")
    String model;

    @Schema(description = "Current allocation state of the GPU")
    GpuState state;

    @Schema(description = "NUMA node affinity for this GPU, if applicable", example = "0")
    Integer numaNodeAffinity;
}
