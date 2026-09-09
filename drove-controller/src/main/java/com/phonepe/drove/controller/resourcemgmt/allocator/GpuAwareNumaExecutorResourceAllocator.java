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

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import com.google.common.base.Preconditions;
import com.phonepe.drove.common.CommonUtils;
import com.phonepe.drove.common.model.utils.Pair;
import com.phonepe.drove.controller.resourcemgmt.ExecutorHostInfo;
import com.phonepe.drove.controller.resourcemgmt.GPUUtils;
import com.phonepe.drove.controller.utils.ControllerUtils;
import com.phonepe.drove.models.application.requirements.GpuType;
import com.phonepe.drove.models.info.ExecutorResourceSnapshot;
import com.phonepe.drove.models.info.GPUInfo;
import com.phonepe.drove.models.info.nodedata.ExecutorNodeData;
import com.phonepe.drove.models.info.resources.PhysicalLayout;
import com.phonepe.drove.models.info.resources.allocation.AllocatedExecutorNode;
import com.phonepe.drove.models.info.resources.allocation.CPUAllocation;
import com.phonepe.drove.models.info.resources.allocation.GPUAllocation;
import com.phonepe.drove.models.info.resources.allocation.MemoryAllocation;

import lombok.val;
import lombok.extern.slf4j.Slf4j;

/**
 * NUMA node selector that prioritizes nodes with the most local free GPUs.
 * This ensures that when GPUs are requested, the selected CPU/memory NUMA node
 * is the one that already has enough local GPUs, avoiding unnecessary cross-NUMA GPU allocation.
 *
 * Also encapsulates CPU and memory allocation helpers for NUMA-aware resource allocation.
 */
@Slf4j
public class GpuAwareNumaExecutorResourceAllocator implements ExecutorResourceAllocator {

    public Optional<CandidateExecutorAllocation> allocate(
            ExecutorHostInfo hostInfo,
            long requiredCpus,
            long requiredMemory,
            long requiredGpus) {

        return allocate(hostInfo, requiredCpus, requiredMemory, requiredGpus, null);
    }

    @Override
    public Optional<CandidateExecutorAllocation> allocate(
            ExecutorHostInfo hostInfo,
            long actualRequiredCpus,
            long actualRequiredMemory,
            long requiredGpus,
            GpuType requiredGpuType) {
        Preconditions.checkArgument(requiredGpus == 0 || requiredGpuType != null,
                "GPU type must be specified when GPU count is greater than 0");
        val physicalLayout = hostInfo.getNodeData().getState().getLayout();
        var cpuScaling = physicalLayout.getCpuScalingFactor();
        cpuScaling = CommonUtils.doubleEquals(cpuScaling, 0) ? PhysicalLayout.DEFAULT_CPU_SCALING_FACTOR : cpuScaling;
        var memoryScaling = physicalLayout.getMemoryScalingFactor();
        memoryScaling = CommonUtils.doubleEquals(memoryScaling, 0) ? PhysicalLayout.DEFAULT_MEMORY_SCALING_FACTOR : memoryScaling;
        val requiredCpus = (long) Math.ceil(actualRequiredCpus / cpuScaling);
        val requiredMemory = (long) Math.ceil(actualRequiredMemory / memoryScaling);
        if(requiredCpus != actualRequiredCpus || requiredMemory != actualRequiredMemory) {
            log.info("Scaling resource requirements for executor {}: requested CPUs={} scaled to {}, requested Memory={} scaled to {}",
                    hostInfo.getExecutorId(), actualRequiredCpus, requiredCpus, actualRequiredMemory, requiredMemory);
        }
        val candidateNumaNodeIds = candidateNumaNodeIds(hostInfo, requiredCpus, requiredMemory);
        if (candidateNumaNodeIds.isEmpty()) {
            return Optional.empty();
        }

        if (requiredGpus == 0) {
            return Optional.of(new CandidateExecutorAllocation(buildAllocatedNode(
                    hostInfo,
                    candidateNumaNodeIds.get(0),
                    requiredCpus,
                    requiredMemory,
                    new GPUAllocation(Set.of(), requiredGpuType)), new CandidateAllocationMetadata(0)));
        }

        val layoutGpus = getLayoutGpus(hostInfo);
        val freeGpuIds = getFreeGpuIds(hostInfo, layoutGpus, requiredGpuType);

        val totalFreeGpus = freeGpuIds.size();
        if (totalFreeGpus < requiredGpus) {
            return Optional.empty();
        }

        // Count free GPUs per NUMA node by reading GPUInfo directly from layout
        val localFreeGpuCountByNuma = freeGpuIds.stream()
                .map(layoutGpus::get)
                .filter(Objects::nonNull)
                .filter(gpu -> gpu.getNumaNodeAffinity() != null)
                .collect(Collectors.groupingBy(GPUInfo::getNumaNodeAffinity, Collectors.counting()));

        val sortedCandidates = candidateNumaNodeIds.stream()
                .sorted(Comparator.comparingLong((Integer numaId) ->
                                localFreeGpuCountByNuma.getOrDefault(numaId, 0L))
                        .reversed()
                        .thenComparingInt(numaId -> numaId))
                .toList();

        for (Integer selectedNumaNodeId : sortedCandidates) {
            val gpuAlloc = allocateGPUs(requiredGpus, selectedNumaNodeId, freeGpuIds, layoutGpus);
            if (gpuAlloc.isEmpty()) {
                continue;
            }

            return Optional.of(new CandidateExecutorAllocation(buildAllocatedNode(
                    hostInfo,
                    selectedNumaNodeId,
                    requiredCpus,
                    requiredMemory,
                    gpuAlloc.get().getFirst()), new CandidateAllocationMetadata(gpuAlloc.get().getSecond())));
        }

        return Optional.empty();
    }

    private List<Integer> candidateNumaNodeIds(
            ExecutorHostInfo hostInfo,
            long requiredCpus,
            long requiredMemory) {
        return hostInfo.getNodes()
                .entrySet()
                .stream()
                .filter(entry -> freeCoresForNode(entry.getValue()) >= requiredCpus
                        && entry.getValue().getMemory().getAvailable() >= requiredMemory)
                .map(Map.Entry::getKey)
                .sorted()
                .toList();
    }

    private long freeCoresForNode(ExecutorHostInfo.NumaNodeInfo node) {
        return node.getCores()
                .entrySet()
                .stream()
                .filter(e -> e.getValue().equals(ExecutorHostInfo.CoreState.FREE))
                .count();
    }

    private AllocatedExecutorNode buildAllocatedNode(
            ExecutorHostInfo hostInfo,
            int selectedNumaNodeId,
            long requiredCpus,
            long requiredMemory,
            GPUAllocation gpuAllocation) {
        val numaNodeInfo = Objects.requireNonNull(hostInfo.getNodes().get(selectedNumaNodeId),
                                                  "No NUMA node found for id " + selectedNumaNodeId);

        return new AllocatedExecutorNode(
                hostInfo.getExecutorId(),
                hostInfo.getNodeData().getHostname(),
                hostInfo.getNodeData().getPort(),
                hostInfo.getNodeData().getTransportType(),
                allocateCPUs(Map.entry(selectedNumaNodeId, numaNodeInfo), requiredCpus),
                allocateMemory(selectedNumaNodeId, requiredMemory),
                gpuAllocation,
                Objects.requireNonNullElse(hostInfo.getNodeData().getTags(), Set.of()),
                Objects.requireNonNullElse(hostInfo.getNodeData().getMetadata(), Map.of()),
                hostInfo.getNodeData().getExecutorState());
    }

    private MemoryAllocation allocateMemory(int numaNodeId, long requiredMemory) {
        return new MemoryAllocation(Map.of(numaNodeId, requiredMemory));
    }

    private CPUAllocation allocateCPUs(
            Map.Entry<Integer, ExecutorHostInfo.NumaNodeInfo> nodeInfo,
            long requiredCpus) {
        return new CPUAllocation(Map.of(nodeInfo.getKey(),
                                        nodeInfo.getValue()
                                                .getCores()
                                                .entrySet()
                                                .stream()
                                                .filter(entry -> entry.getValue()
                                                        .equals(ExecutorHostInfo.CoreState.FREE))
                                                .map(Map.Entry::getKey)
                                                .limit(requiredCpus)
                                                .collect(Collectors.toUnmodifiableSet())));
    }

    private Map<String, GPUInfo> getLayoutGpus(ExecutorHostInfo hostInfo) {
        return Optional.ofNullable(hostInfo.getNodeData())
                .map(ExecutorNodeData::getState)
                .map(ExecutorResourceSnapshot::getLayout)
                .map(PhysicalLayout::getGpus)
                .orElse(Map.of());
    }

    /**
     * Get the set of free GPU IDs across all NUMA nodes after applying optional GPU type filtering.
     */
    private Set<String> getFreeGpuIds(
            ExecutorHostInfo hostInfo,
            Map<String, GPUInfo> layoutGpus,
            GpuType requiredGpuType) {
        if (hostInfo.getGpuInfo() == null) {
            return Set.of();
        }
        return hostInfo.getGpuInfo().getGpus().entrySet().stream()
                .filter(entry -> entry.getValue().equals(ExecutorHostInfo.CoreState.FREE))
                .filter(entry -> GPUUtils.matches(layoutGpus.get(entry.getKey()), requiredGpuType))
                .map(Map.Entry::getKey)
                .collect(Collectors.toUnmodifiableSet());
    }

    /**
     * Allocate GPUs preferring those local to the selected NUMA node, falling back to cross-NUMA if needed.
     * Returns a GPUAllocation with localGpuCount set to the number of NUMA-local GPUs allocated.
     */
    private Optional<Pair<GPUAllocation, Integer>> allocateGPUs(
            long requiredGpus,
            int preferredNumaNode,
            Set<String> freeGpuIds,
            Map<String, GPUInfo> layoutGpus) {
        if (requiredGpus == 0) {
            return Optional.of(Pair.of(new GPUAllocation(Set.of(), null), 0));
        }

        if (freeGpuIds.size() < requiredGpus) {
            return Optional.empty();
        }

        // Prefer GPUs on the same NUMA node as the CPU allocation
        val numaLocalGpus = freeGpuIds.stream()
                .sorted()
                .filter(gpuId -> Objects.equals(
                        Optional.ofNullable(layoutGpus.get(gpuId))
                                .map(GPUInfo::getNumaNodeAffinity)
                                .orElse(null),
                        preferredNumaNode))
                .limit(requiredGpus)
                .collect(Collectors.toUnmodifiableSet());


        if (numaLocalGpus.size() == requiredGpus) {
            // All allocated GPUs are NUMA-local
            val resolvedGpuType = resolveGpuType(numaLocalGpus, layoutGpus);
            return Optional.of(Pair.of(new GPUAllocation(numaLocalGpus, resolvedGpuType), numaLocalGpus.size()));
        }

        // Not enough NUMA-local GPUs; fall back to any free GPUs
        // localGpuCount is still the number of NUMA-local GPUs we managed to allocate
        val allocatedGpus = freeGpuIds.stream()
                .sorted()
                .limit(requiredGpus)
                .collect(Collectors.toUnmodifiableSet());
        val resolvedGpuTypeFallback = resolveGpuType(allocatedGpus, layoutGpus);
        return Optional.of(Pair.of(new GPUAllocation(allocatedGpus, resolvedGpuTypeFallback), numaLocalGpus.size()));
    }

    private GpuType resolveGpuType(Set<String> allocatedGpuIds, Map<String, GPUInfo> layoutGpus) {
        return allocatedGpuIds.stream()
                .map(layoutGpus::get)
                .filter(Objects::nonNull)
                .findFirst()
                .map(info -> new GpuType(info.getGpuVendor(), info.getGpuModel()))
                .orElse(null);
    }

}

