/*
 * Copyright (c) 2026 Original Author(s), PhonePe India Pvt. Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.phonepe.drove.executor.resourcemgmt;

import com.phonepe.drove.models.info.resources.PhysicalLayout;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.Value;
import lombok.With;
import lombok.extern.jackson.Jacksonized;

@Value
@Builder
@With
@Jacksonized
@NoArgsConstructor
@AllArgsConstructor
public class PerformanceScalingConfig {

    public static final PerformanceScalingConfig DEFAULT = PerformanceScalingConfig.builder()
            .cpuScalingFactor(PhysicalLayout.DEFAULT_CPU_SCALING_FACTOR)
            .memoryScalingFactor(PhysicalLayout.DEFAULT_MEMORY_SCALING_FACTOR)
            .gpuScalingFactor(PhysicalLayout.DEFAULT_GPU_SCALING_FACTOR)
            .build();

    @Builder.Default
    Double cpuScalingFactor = PhysicalLayout.DEFAULT_CPU_SCALING_FACTOR;
    @Builder.Default
    Double memoryScalingFactor = PhysicalLayout.DEFAULT_MEMORY_SCALING_FACTOR;
    @Builder.Default
    Double gpuScalingFactor = PhysicalLayout.DEFAULT_GPU_SCALING_FACTOR;
}
