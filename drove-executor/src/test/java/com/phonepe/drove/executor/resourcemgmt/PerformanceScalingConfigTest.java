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

package com.phonepe.drove.executor.resourcemgmt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;

import com.phonepe.drove.models.info.resources.PhysicalLayout;

import org.junit.jupiter.api.Test;

import lombok.val;

class PerformanceScalingConfigTest {

    @Test
    void defaultValuesMatchPhysicalLayoutConstants() {
        val config = PerformanceScalingConfig.builder().build();
        assertEquals(PhysicalLayout.DEFAULT_CPU_SCALING_FACTOR, config.getCpuScalingFactor().doubleValue(), 0.001);
        assertEquals(PhysicalLayout.DEFAULT_MEMORY_SCALING_FACTOR, config.getMemoryScalingFactor().doubleValue(), 0.001);
        assertEquals(PhysicalLayout.DEFAULT_GPU_SCALING_FACTOR, config.getGpuScalingFactor().doubleValue(), 0.001);
    }

    @Test
    void defaultConstantMatchesBuilderDefault() {
        val config = PerformanceScalingConfig.DEFAULT;
        assertEquals(PhysicalLayout.DEFAULT_CPU_SCALING_FACTOR, config.getCpuScalingFactor().doubleValue(), 0.001);
        assertEquals(PhysicalLayout.DEFAULT_MEMORY_SCALING_FACTOR, config.getMemoryScalingFactor().doubleValue(), 0.001);
        assertEquals(PhysicalLayout.DEFAULT_GPU_SCALING_FACTOR, config.getGpuScalingFactor().doubleValue(), 0.001);
    }

    @Test
    void customScalingFactorsViaBuilder() {
        val config = PerformanceScalingConfig.builder()
                .cpuScalingFactor(2.5)
                .memoryScalingFactor(0.8)
                .gpuScalingFactor(1.5)
                .build();
        assertEquals(2.5, config.getCpuScalingFactor().doubleValue(), 0.001);
        assertEquals(0.8, config.getMemoryScalingFactor().doubleValue(), 0.001);
        assertEquals(1.5, config.getGpuScalingFactor().doubleValue(), 0.001);
    }

    @Test
    void allScalingFactorsCanBeZero() {
        val config = PerformanceScalingConfig.builder()
                .cpuScalingFactor(0.0)
                .memoryScalingFactor(0.0)
                .gpuScalingFactor(0.0)
                .build();
        assertEquals(0.0, config.getCpuScalingFactor().doubleValue(), 0.001);
        assertEquals(0.0, config.getMemoryScalingFactor().doubleValue(), 0.001);
        assertEquals(0.0, config.getGpuScalingFactor().doubleValue(), 0.001);
    }

    @Test
    void withMethodsProduceNewInstances() {
        val original = PerformanceScalingConfig.DEFAULT;
        val modified = original.withCpuScalingFactor(3.0)
                              .withMemoryScalingFactor(2.0)
                              .withGpuScalingFactor(0.5);
        assertNotSame(original, modified);
        assertEquals(PhysicalLayout.DEFAULT_CPU_SCALING_FACTOR, original.getCpuScalingFactor().doubleValue(), 0.001);
        assertEquals(3.0, modified.getCpuScalingFactor().doubleValue(), 0.001);
        assertEquals(2.0, modified.getMemoryScalingFactor().doubleValue(), 0.001);
        assertEquals(0.5, modified.getGpuScalingFactor().doubleValue(), 0.001);
    }
}
