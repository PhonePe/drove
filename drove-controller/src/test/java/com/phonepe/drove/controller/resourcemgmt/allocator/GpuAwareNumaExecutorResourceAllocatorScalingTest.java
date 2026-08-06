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

package com.phonepe.drove.controller.resourcemgmt.allocator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.phonepe.drove.controller.resourcemgmt.ExecutorHostInfo;
import com.phonepe.drove.models.application.requirements.GpuType;
import com.phonepe.drove.models.application.requirements.GpuVendor;
import com.phonepe.drove.models.info.ExecutorResourceSnapshot;
import com.phonepe.drove.models.info.GPUInfo;
import com.phonepe.drove.models.info.nodedata.ExecutorNodeData;
import com.phonepe.drove.models.info.nodedata.ExecutorState;
import com.phonepe.drove.models.info.nodedata.NodeTransportType;
import com.phonepe.drove.models.info.resources.PhysicalLayout;
import com.phonepe.drove.models.info.resources.available.AvailableCPU;
import com.phonepe.drove.models.info.resources.available.AvailableGPU;
import com.phonepe.drove.models.info.resources.available.AvailableMemory;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import lombok.val;

class GpuAwareNumaExecutorResourceAllocatorScalingTest {

    private final GpuAwareNumaExecutorResourceAllocator allocator =
            new GpuAwareNumaExecutorResourceAllocator();

    private static final GpuType NVIDIA_H100 =
            new GpuType(GpuVendor.NVIDIA, "H100");

    @ParameterizedTest(name = "{0}")
    @MethodSource("cpuScalingCases")
    void cpuScaling(
            String name,
            int freeCoreCount,
            long requestedCpus,
            double cpuScaling,
            boolean expectSuccess,
            int expectedAllocatedCores) {
        val cores = new LinkedHashSet<Integer>();
        for (var i = 0; i < freeCoreCount; i++) {
            cores.add(i);
        }
        val hostInfo = buildHostInfo(
                Map.of(0, cores),
                Map.of(0, 4096L),
                cpuScaling, 1.0);

        val result = allocator.allocate(hostInfo, requestedCpus, 128, 0);

        if (expectSuccess) {
            assertTrue(result.isPresent(), name + ": allocation should succeed");
            val allocatedCores = result.get().getAllocatedNode().getCpu().getCores().get(0);
            assertEquals(expectedAllocatedCores, allocatedCores.size(),
                         name + ": unexpected number of allocated cores");
        }
        else {
            assertTrue(result.isEmpty(), name + ": allocation should fail");
        }
    }

    static Stream<Arguments> cpuScalingCases() {
        return Stream.of(
                Arguments.of("factor 2.0 reduces demand - succeeds with 1 free core",
                             1, 2, 2.0, true, 1),
                Arguments.of("factor 2.0 fails when scaled demand still exceeds free",
                             1, 4, 2.0, false, 0),
                Arguments.of("factor 0.5 increases demand - fails when not enough cores",
                             2, 2, 0.5, false, 0),
                Arguments.of("factor 0.5 increases demand - succeeds with enough cores",
                             4, 2, 0.5, true, 4),
                Arguments.of("factor 1.5 ceiling rounding succeeds",
                             2, 3, 1.5, true, 2),
                Arguments.of("factor 1.5 ceiling rounding causes failure",
                             1, 3, 1.5, false, 0),
                Arguments.of("very high factor 100.0 reduces demand to 1 core",
                             1, 50, 100.0, true, 1)
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("memoryScalingCases")
    void memoryScaling(
            String name,
            long freeMemory,
            long requestedMemory,
            double memoryScaling,
            boolean expectSuccess,
            long expectedAllocated) {
        val hostInfo = buildHostInfo(
                Map.of(0, Set.of(0, 1)),
                Map.of(0, freeMemory),
                1.0, memoryScaling);

        val result = allocator.allocate(hostInfo, 1, requestedMemory, 0);

        if (expectSuccess) {
            assertTrue(result.isPresent(), name + ": allocation should succeed");
            val memAlloc = result.get().getAllocatedNode().getMemory().getMemoryInMB();
            assertEquals(expectedAllocated, memAlloc.get(0),
                         name + ": unexpected allocated memory");
        }
        else {
            assertTrue(result.isEmpty(),
                       name + ": allocation should fail");
        }
    }

    static Stream<Arguments> memoryScalingCases() {
        return Stream.of(
                Arguments.of("factor 2.0 reduces demand - succeeds",
                             64L, 128L, 2.0, true, 64L),
                Arguments.of("factor 2.0 fails when scaled demand exceeds free",
                             63L, 128L, 2.0, false, 0L),
                Arguments.of("factor 0.5 increases demand - succeeds with enough memory",
                             256L, 128L, 0.5, true, 256L),
                Arguments.of("factor 0.5 increases demand - fails",
                             255L, 128L, 0.5, false, 0L),
                Arguments.of("factor 1.5 ceiling rounding succeeds",
                             67L, 100L, 1.5, true, 67L)
        );
    }

    @Test
    void bothScalingFactorsApplyTogether() {
        // cpu=1.5: ceil(2/1.5)=2 cores; mem=2.0: ceil(100/2.0)=50 MB; host has exactly both.
        val hostInfo = buildHostInfo(
                Map.of(0, Set.of(0, 1)),
                Map.of(0, 50L),
                1.5, 2.0);

        val result = allocator.allocate(hostInfo, 2, 100, 0);

        assertTrue(result.isPresent());
        val allocated = result.get().getAllocatedNode();
        assertEquals(2, allocated.getCpu().getCores().get(0).size(),
                      "ceil(2/1.5)=2");
        assertEquals(50L, allocated.getMemory().getMemoryInMB().get(0),
                      "ceil(100/2.0)=50");
    }

    @Test
    void bothScalingFactorsMayCauseFail() {
        // 2 free cores satisfy scaled CPU (ceil(2/1.5)=2); 49 MB does not satisfy scaled memory (ceil(100/2.0)=50).
        val hostInfo = buildHostInfo(
                Map.of(0, Set.of(0, 1)),
                Map.of(0, 49L),
                1.5, 2.0);

        val result = allocator.allocate(hostInfo, 2, 100, 0);

        assertTrue(result.isEmpty());
    }

    @Test
    void defaultScalingKeepsDemandUnchanged() {
        val hostInfo = buildHostInfo(
                Map.of(0, Set.of(0, 1, 2, 3)),
                Map.of(0, 1024L),
                PhysicalLayout.DEFAULT_CPU_SCALING_FACTOR,
                PhysicalLayout.DEFAULT_MEMORY_SCALING_FACTOR);

        val result = allocator.allocate(hostInfo, 2, 512, 0);

        assertTrue(result.isPresent());
        val allocated = result.get().getAllocatedNode();
        assertEquals(2, allocated.getCpu().getCores().get(0).size());
        assertEquals(512L, allocated.getMemory().getMemoryInMB().get(0));
    }

    @Test
    void gpuCountIgnoresCpuScaling() {
        // cpu=3.0: ceil(1/3.0)=1 core needed; GPU count must remain 1.
        val hostInfo = buildHostInfoWithGpus(
                Map.of(0, Set.of(0, 1, 2)),
                Map.of(0, 2048L),
                Map.of("GPU-0", new GPUInfo("GPU-0", GpuVendor.NVIDIA, "H100", 0)),
                3.0, 1.0);

        val result = allocator.allocate(hostInfo, 1, 128, 1, NVIDIA_H100);

        assertTrue(result.isPresent());
        val allocated = result.get().getAllocatedNode();
        assertEquals(1, allocated.getGpu().getGpuIds().size());
        assertEquals(1, allocated.getCpu().getCores().get(0).size(),
                      "ceil(1/3.0)=1");
    }

    @Test
    void gpuCountIgnoresMemoryScaling() {
        // mem=4.0: ceil(512/4.0)=128 MB needed; GPU count must remain 2.
        val hostInfo = buildHostInfoWithGpus(
                Map.of(0, Set.of(0, 1)),
                Map.of(0, 4096L),
                Map.of("GPU-0", new GPUInfo("GPU-0", GpuVendor.NVIDIA, "H100", 0),
                       "GPU-1", new GPUInfo("GPU-1", GpuVendor.NVIDIA, "H100", 0)),
                1.0, 4.0);

        val result = allocator.allocate(hostInfo, 1, 512, 2, NVIDIA_H100);

        assertTrue(result.isPresent());
        val allocated = result.get().getAllocatedNode();
        assertEquals(2, allocated.getGpu().getGpuIds().size());
        assertEquals(128L, allocated.getMemory().getMemoryInMB().get(0),
                      "ceil(512/4.0)=128");
    }

    @Test
    void scalingSelectsNumaNodeWithEnoughCores() {
        // NUMA 0: 2 cores; NUMA 1: 4 cores; cpu=0.5 -> ceil(2/0.5)=4 -> must use NUMA 1.
        val hostInfo = buildHostInfo(
                Map.of(0, Set.of(0, 1), 1, Set.of(2, 3, 4, 5)),
                Map.of(0, 1024L, 1, 1024L),
                0.5, 1.0);

        val result = allocator.allocate(hostInfo, 2, 128, 0);

        assertTrue(result.isPresent());
        val cpuNumaNode = result.get().getAllocatedNode().getCpu().getCores().keySet().iterator().next();
        assertEquals(1, cpuNumaNode,
                      "NUMA 0 has only 2 free cores; ceil(2/0.5)=4 needed");
    }

    @Test
    void scalingFailsWhenNoCpuCapacity() {
        // Both NUMA nodes have 2 cores; cpu=0.5 -> ceil(2/0.5)=4 -> no node qualifies.
        val hostInfo = buildHostInfo(
                Map.of(0, Set.of(0, 1), 1, Set.of(2, 3)),
                Map.of(0, 1024L, 1, 1024L),
                0.5, 1.0);

        val result = allocator.allocate(hostInfo, 2, 128, 0);

        assertTrue(result.isEmpty());
    }

    @Test
    void scalingFailsWhenNoMemoryCapacity() {
        // Both NUMA nodes have 100 MB; mem=0.5 -> ceil(64/0.5)=128 MB -> no node qualifies.
        val hostInfo = buildHostInfo(
                Map.of(0, Set.of(0, 1), 1, Set.of(2, 3)),
                Map.of(0, 100L, 1, 100L),
                1.0, 0.5);

        val result = allocator.allocate(hostInfo, 1, 64, 0);

        assertTrue(result.isEmpty());
    }

    @Test
    void cpuScalingRespectsNumaLocality() {
        // Each NUMA node has 1 local GPU; cpu=2.0 -> ceil(1/2.0)=1 core needed.
        // Both NUMA nodes satisfy demand; allocator picks NUMA 0 for GPU locality.
        val hostInfo = buildHostInfoWithGpus(
                Map.of(0, Set.of(0, 1), 1, Set.of(2, 3, 4, 5)),
                Map.of(0, 2048L, 1, 2048L),
                Map.of(
                        "GPU-0", new GPUInfo("GPU-0", GpuVendor.NVIDIA, "H100", 0),
                        "GPU-1", new GPUInfo("GPU-1", GpuVendor.NVIDIA, "H100", 1)),
                2.0, 1.0);

        val result = allocator.allocate(hostInfo, 1, 128, 1, NVIDIA_H100);

        assertTrue(result.isPresent());
        val allocated = result.get().getAllocatedNode();
        val cpuNumaNode = allocated.getCpu().getCores().keySet().iterator().next();
        assertEquals(0, cpuNumaNode);
        assertEquals(1, allocated.getCpu().getCores().get(0).size(),
                      "ceil(1/2.0)=1");
        assertEquals(1, allocated.getGpu().getGpuIds().size());
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
                "test-host", 8080, NodeTransportType.HTTP, new Date(),
                snapshot, List.of(), List.of(), List.of(),
                Set.of(), Map.of(), ExecutorState.ACTIVE);

        val numaNodes = new HashMap<Integer, ExecutorHostInfo.NumaNodeInfo>();
        freeCoresPerNuma.forEach((numaId, cores) -> {
            val info = numaNodes.computeIfAbsent(numaId, k -> new ExecutorHostInfo.NumaNodeInfo());
            cores.forEach(c -> info.getCores().put(c, ExecutorHostInfo.CoreState.FREE));
            info.getMemory().setAvailable(freeMemoryPerNuma.get(numaId));
        });

        return new ExecutorHostInfo("TEST_EXECUTOR", nodeData, numaNodes,
                                     new ExecutorHostInfo.HostGPUInfo());
    }

    private ExecutorHostInfo buildHostInfoWithGpus(
            Map<Integer, Set<Integer>> freeCoresPerNuma,
            Map<Integer, Long> freeMemoryPerNuma,
            Map<String, GPUInfo> gpus,
            double cpuScalingFactor,
            double memoryScalingFactor) {
        val usedMemory = freeMemoryPerNuma.keySet().stream()
                .collect(Collectors.toUnmodifiableMap(k -> k, k -> 0L));

        val freeGpuIds = gpus.keySet().stream().toList();
        val snapshot = new ExecutorResourceSnapshot(
                "TEST_EXECUTOR",
                new AvailableCPU(freeCoresPerNuma, Map.of()),
                new AvailableMemory(freeMemoryPerNuma, usedMemory),
                new AvailableGPU(freeGpuIds, List.of()),
                new PhysicalLayout(freeCoresPerNuma,
                                   freeMemoryPerNuma,
                                   gpus,
                                   cpuScalingFactor,
                                   memoryScalingFactor));

        val nodeData = new ExecutorNodeData(
                "test-host", 8080, NodeTransportType.HTTP, new Date(),
                snapshot, List.of(), List.of(), List.of(),
                Set.of(), Map.of(), ExecutorState.ACTIVE);

        val numaNodes = new HashMap<Integer, ExecutorHostInfo.NumaNodeInfo>();
        freeCoresPerNuma.forEach((numaId, cores) -> {
            val info = numaNodes.computeIfAbsent(numaId, k -> new ExecutorHostInfo.NumaNodeInfo());
            cores.forEach(c -> info.getCores().put(c, ExecutorHostInfo.CoreState.FREE));
            info.getMemory().setAvailable(freeMemoryPerNuma.get(numaId));
        });

        val gpuInfo = new ExecutorHostInfo.HostGPUInfo();
        gpus.values().forEach(gpu ->
                gpuInfo.getGpus().put(gpu.getGpuId(), ExecutorHostInfo.CoreState.FREE));

        return new ExecutorHostInfo("TEST_EXECUTOR", nodeData, numaNodes, gpuInfo);
    }
}
