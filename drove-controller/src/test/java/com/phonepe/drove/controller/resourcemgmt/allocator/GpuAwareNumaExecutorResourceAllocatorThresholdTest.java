/*
 * Copyright (c) 2024 Original Author(s), PhonePe India Pvt. Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.phonepe.drove.controller.resourcemgmt.allocator;

import com.phonepe.drove.controller.ControllerTestUtils;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.phonepe.drove.controller.resourcemgmt.ExecutorHostInfo;
import com.phonepe.drove.models.info.ExecutorResourceSnapshot;
import com.phonepe.drove.models.info.nodedata.ExecutorNodeData;
import com.phonepe.drove.models.info.nodedata.ExecutorState;
import com.phonepe.drove.models.info.nodedata.NodeTransportType;
import com.phonepe.drove.models.info.resources.PhysicalLayout;
import com.phonepe.drove.models.info.resources.available.AvailableCPU;
import com.phonepe.drove.models.info.resources.available.AvailableGPU;
import com.phonepe.drove.models.info.resources.available.AvailableMemory;

import org.junit.jupiter.api.Test;

import lombok.val;

/**
 * Verifies that scaling factors apply only when the requested amount is at or
 * above the configured minimum. Below the minimum, the allocator uses the raw
 * request and applies no scaling.
 */
class GpuAwareNumaExecutorResourceAllocatorThresholdTest {

    private static final long MIN_CPUS = 4;
    private static final long MIN_MEMORY_MB = 512;

    @Test
    void belowMinimumCpusUsesRawRequestAndSkipsCpuScaling() {
        // Request 2 CPUs with factor 0.5. Scaling would give ceil(2/0.5)=4, but
        // 2 < MIN_CPUS, so no scaling applies and only 2 cores are allocated.
        val hostInfo = buildHostInfo(Map.of(0, Set.of(0, 1, 2, 3)), Map.of(0, 1024L), 0.5, 1.0);
        val allocator = ControllerTestUtils.gpuAwareAllocator(MIN_CPUS, MIN_MEMORY_MB);

        val result = allocator.allocate(hostInfo, 2, 128, 0);

        assertTrue(result.isPresent());
        assertEquals(Set.of(0, 1), result.get().getAllocatedNode().getCpu().getCores().get(0));
    }

    @Test
    void belowMinimumCpusSucceedsWhenHostHasExactlyRawRequest() {
        // Request 2 CPUs with factor 0.5 and only 2 free cores. Scaling would
        // fail, but the raw request fits because 2 < MIN_CPUS.
        val hostInfo = buildHostInfo(Map.of(0, Set.of(0, 1)), Map.of(0, 1024L), 0.5, 1.0);
        val allocator = ControllerTestUtils.gpuAwareAllocator(MIN_CPUS, MIN_MEMORY_MB);

        val result = allocator.allocate(hostInfo, 2, 128, 0);

        assertTrue(result.isPresent());
        assertEquals(2, result.get().getAllocatedNode().getCpu().getCores().get(0).size());
    }

    @Test
    void atMinimumCpusAppliesCpuScaling() {
        // Request exactly MIN_CPUS with factor 2.0. At the threshold,
        // scaling applies: ceil(4/2.0)=2 cores are allocated.
        val hostInfo = buildHostInfo(Map.of(0, Set.of(0, 1, 2, 3)), Map.of(0, 1024L), 2.0, 1.0);
        val allocator = ControllerTestUtils.gpuAwareAllocator(MIN_CPUS, MIN_MEMORY_MB);

        val result = allocator.allocate(hostInfo, MIN_CPUS, 128, 0);

        assertTrue(result.isPresent());
        assertEquals(2, result.get().getAllocatedNode().getCpu().getCores().get(0).size());
    }

    @Test
    void aboveMinimumCpusAppliesCpuScaling() {
        // Request 8 CPUs with factor 2.0. Scaling applies: ceil(8/2.0)=4 cores.
        val hostInfo = buildHostInfo(Map.of(0, Set.of(0, 1, 2, 3)), Map.of(0, 1024L), 2.0, 1.0);
        val allocator = ControllerTestUtils.gpuAwareAllocator(MIN_CPUS, MIN_MEMORY_MB);

        val result = allocator.allocate(hostInfo, 8, 128, 0);

        assertTrue(result.isPresent());
        assertEquals(4, result.get().getAllocatedNode().getCpu().getCores().get(0).size());
    }

    @Test
    void belowMinimumMemoryUsesRawRequestAndSkipsMemoryScaling() {
        // Request 256 MB with factor 0.5. Scaling would give ceil(256/0.5)=512,
        // but 256 < MIN_MEMORY_MB, so no scaling applies and 256 MB is allocated.
        val hostInfo = buildHostInfo(Map.of(0, Set.of(0, 1)), Map.of(0, 256L), 1.0, 0.5);
        val allocator = ControllerTestUtils.gpuAwareAllocator(MIN_CPUS, MIN_MEMORY_MB);

        val result = allocator.allocate(hostInfo, 1, 256, 0);

        assertTrue(result.isPresent());
        assertEquals(256L, result.get().getAllocatedNode().getMemory().getMemoryInMB().get(0));
    }

    @Test
    void atMinimumMemoryAppliesMemoryScaling() {
        // Request exactly MIN_MEMORY_MB with factor 2.0. At the threshold,
        // scaling applies: ceil(512/2.0)=256 MB is allocated.
        val hostInfo = buildHostInfo(Map.of(0, Set.of(0, 1)), Map.of(0, 256L), 1.0, 2.0);
        val allocator = ControllerTestUtils.gpuAwareAllocator(MIN_CPUS, MIN_MEMORY_MB);

        val result = allocator.allocate(hostInfo, 1, MIN_MEMORY_MB, 0);

        assertTrue(result.isPresent());
        assertEquals(256L, result.get().getAllocatedNode().getMemory().getMemoryInMB().get(0));
    }

    @Test
    void cpuAndMemoryThresholdsApplyIndependently() {
        // CPU request is below its minimum: no CPU scaling. Memory request is
        // above its minimum: memory scaling applies.
        val hostInfo = buildHostInfo(Map.of(0, Set.of(0, 1, 2, 3)), Map.of(0, 512L), 0.5, 2.0);
        val allocator = ControllerTestUtils.gpuAwareAllocator(MIN_CPUS, MIN_MEMORY_MB);

        val result = allocator.allocate(hostInfo, 2, 1024, 0);

        assertTrue(result.isPresent());
        val allocated = result.get().getAllocatedNode();
        assertEquals(2, allocated.getCpu().getCores().get(0).size(), "no CPU scaling below minimum");
        assertEquals(512L, allocated.getMemory().getMemoryInMB().get(0), "ceil(1024/2.0)=512");
    }

    @Test
    void defaultsMatchProductionThresholds() {
        // The default allocator must gate scaling with the production defaults
        // (2 CPUs / 128 MB). Below the minimum no scaling applies.
        val hostInfo = buildHostInfo(Map.of(0, Set.of(0, 1, 2, 3)), Map.of(0, 100L), 0.5, 0.5);
        val allocator = ControllerTestUtils.gpuAwareAllocator();

        val result = allocator.allocate(hostInfo, 1, 100, 0);

        assertTrue(result.isPresent());
        val allocated = result.get().getAllocatedNode();
        assertEquals(1, allocated.getCpu().getCores().get(0).size(), "no CPU scaling below 2 CPUs");
        assertEquals(100L, allocated.getMemory().getMemoryInMB().get(0), "no memory scaling below 128 MB");
    }

    private ExecutorHostInfo buildHostInfo(
                                           Map<Integer, Set<Integer>> freeCoresPerNuma,
                                           Map<Integer, Long> freeMemoryPerNuma,
                                           double cpuScalingFactor,
                                           double memoryScalingFactor) {
        val usedMemory = freeMemoryPerNuma.keySet().stream()
                .collect(Collectors.toUnmodifiableMap(k -> k, k -> 0L));

        val snapshot = new ExecutorResourceSnapshot(
                                                    "TEST_EXECUTOR",
                                                    new AvailableCPU(freeCoresPerNuma, Map.of()),
                                                    new AvailableMemory(freeMemoryPerNuma, usedMemory),
                                                    new AvailableGPU(List.of(), List.of()),
                                                    new PhysicalLayout(freeCoresPerNuma,
                                                                       freeMemoryPerNuma,
                                                                       Map.of(),
                                                                       cpuScalingFactor,
                                                                       memoryScalingFactor));

        val nodeData = new ExecutorNodeData(
                                            "test-host",
                                            8080,
                                            NodeTransportType.HTTP,
                                            new Date(),
                                            snapshot,
                                            List.of(),
                                            List.of(),
                                            List.of(),
                                            Set.of(),
                                            Map.of(),
                                            ExecutorState.ACTIVE);

        val numaNodes = new HashMap<Integer, ExecutorHostInfo.NumaNodeInfo>();
        freeCoresPerNuma.forEach((numaId, cores) -> {
            val info = numaNodes.computeIfAbsent(numaId, k -> new ExecutorHostInfo.NumaNodeInfo());
            cores.forEach(c -> info.getCores().put(c, ExecutorHostInfo.CoreState.FREE));
            info.getMemory().setAvailable(freeMemoryPerNuma.get(numaId));
        });

        return new ExecutorHostInfo("TEST_EXECUTOR",
                                    nodeData,
                                    numaNodes,
                                    new ExecutorHostInfo.HostGPUInfo());
    }
}
