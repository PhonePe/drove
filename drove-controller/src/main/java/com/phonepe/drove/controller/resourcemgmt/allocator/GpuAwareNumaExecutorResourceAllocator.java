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

import com.domain.Tuple;
import com.google.common.base.Preconditions;
import com.phonepe.drove.controller.resourcemgmt.AllocatedExecutorNode;
import com.phonepe.drove.controller.resourcemgmt.ExecutorHostInfo;
import com.phonepe.drove.controller.resourcemgmt.GPUUtils;
import com.phonepe.drove.models.application.requirements.GpuType;
import com.phonepe.drove.models.info.ExecutorResourceSnapshot;
import com.phonepe.drove.models.info.GPUInfo;
import com.phonepe.drove.models.info.nodedata.ExecutorNodeData;
import com.phonepe.drove.models.info.resources.PhysicalLayout;
import com.phonepe.drove.models.info.resources.allocation.CPUAllocation;
import com.phonepe.drove.models.info.resources.allocation.GPUAllocation;
import com.phonepe.drove.models.info.resources.allocation.MemoryAllocation;
import io.vavr.Tuple2;
import lombok.val;

import java.util.*;
import java.util.stream.Collectors;

/**
 * NUMA node selector that prioritizes nodes with the most local free GPUs.
 * This ensures that when GPUs are requested, the selected CPU/memory NUMA node
 * is the one that already has enough local GPUs, avoiding unnecessary cross-NUMA GPU allocation.
 *
 * Also encapsulates CPU and memory allocation helpers for NUMA-aware resource allocation.
 */
public class GpuAwareNumaExecutorResourceAllocator implements ExecutorResourceAllocator {

    public Optional<ExecutorAllocation> allocate(
            ExecutorHostInfo hostInfo,
            long requiredCpus,
            long requiredMemory,
            long requiredGpus) {

        return allocate(hostInfo, requiredCpus, requiredMemory, requiredGpus, null);
    }

    @Override
    public Optional<ExecutorAllocation> allocate(
            ExecutorHostInfo hostInfo,
            long requiredCpus,
            long requiredMemory,
            long requiredGpus,
            GpuType requiredGpuType) {
        Preconditions.checkArgument(requiredGpus== 0 || requiredGpuType != null, "GPU type must be specified when GPU count is greater than 0");
        val candidateNumaNodeIds = candidateNumaNodeIds(hostInfo, requiredCpus, requiredMemory);
        if (candidateNumaNodeIds.isEmpty()) {
            return Optional.empty();
        }

        if(requiredGpus == 0) {
            return Optional.of(new ExecutorAllocation(buildAllocatedNode(
                    hostInfo,
                    candidateNumaNodeIds.get(0),
                    requiredCpus,
                    requiredMemory,
                    new GPUAllocation(Set.of(), requiredGpuType)), new AllocationMetadata(0)));
        }

        val gpuMetadata = getGpuMetadataById(hostInfo);
        val freeGpuIds = getFreeGpuIds(hostInfo, gpuMetadata, requiredGpuType);

        val totalFreeGpus = freeGpuIds.size();
        if (totalFreeGpus < requiredGpus) {
            return Optional.empty();
        }

        val gpuNumaAffinities = getGpuNumaAffinities(hostInfo);

        val localFreeGpuCountByNuma = freeGpuIds.stream()
                .filter(gpuNumaAffinities::containsKey)
                .collect(Collectors.groupingBy(gpuNumaAffinities::get, Collectors.counting()));

        val sortedCandidates = candidateNumaNodeIds.stream()
                .sorted(Comparator.comparingLong((Integer numaId) -> localFreeGpuCountByNuma.getOrDefault(numaId, 0L))
                                .reversed()
                                .thenComparingInt(numaId -> numaId))
                .toList();

        for (Integer selectedNumaNodeId : sortedCandidates) {
            val gpuAlloc = allocateGPUs(requiredGpus, selectedNumaNodeId, freeGpuIds, gpuNumaAffinities, gpuMetadata);
            if (gpuAlloc.isEmpty()) {
                continue;
            }

            return Optional.of(new ExecutorAllocation(buildAllocatedNode(
                    hostInfo,
                    selectedNumaNodeId,
                    requiredCpus,
                    requiredMemory,
                    gpuAlloc.get()._1()), new AllocationMetadata(gpuAlloc.get()._2())));
        }

        return Optional.empty();
    }

    // -------------------------------------------------------------------------
    // NUMA candidate and CPU/memory allocation helpers
    // -------------------------------------------------------------------------

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

    // -------------------------------------------------------------------------
    // GPU metadata and allocation helpers
    // -------------------------------------------------------------------------

    private Map<String, GPUInfo> getGpuMetadataById(ExecutorHostInfo hostInfo) {
        return Optional.ofNullable(hostInfo.getNodeData())
                .map(ExecutorNodeData::getState)
                .map(ExecutorResourceSnapshot::getLayout)
                .map(PhysicalLayout::getGpus)
                .orElse(Set.of())
                .stream()
                .collect(Collectors.toUnmodifiableMap(
                        GPUInfo::getGpuId,
                        gpu -> gpu,
                        (left, right) -> left));
    }

    /**
     * Get the set of free GPU IDs across all NUMA nodes after applying optional GPU type filtering.
     */
    private Set<String> getFreeGpuIds(
            ExecutorHostInfo hostInfo,
            Map<String, GPUInfo> gpuMetadata,
            GpuType requiredGpuType) {
        return hostInfo.getGpuInfo().getGpus().entrySet().stream()
                .filter(entry -> entry.getValue().equals(ExecutorHostInfo.CoreState.FREE))
                .filter(entry -> GPUUtils.matches(gpuMetadata.get(entry.getKey()), requiredGpuType))
                .map(Map.Entry::getKey)
                .collect(Collectors.toUnmodifiableSet());
    }

    /**
     * Extract GPU ID to NUMA node affinity mapping from the executor's physical layout.
     */
    private Map<String, Integer> getGpuNumaAffinities(ExecutorHostInfo hostInfo) {
        return Optional.ofNullable(hostInfo.getNodeData())
                .map(ExecutorNodeData::getState)
                .map(ExecutorResourceSnapshot::getLayout)
                .map(PhysicalLayout::getGpus)
                .orElse(Set.of())
                .stream()
                .filter(gpu -> gpu.getNumaNodeAffinity() != null)
                .collect(Collectors.toUnmodifiableMap(
                        GPUInfo::getGpuId,
                        GPUInfo::getNumaNodeAffinity,
                        (left, right) -> left));
    }

    /**
     * Allocate GPUs preferring those local to the selected NUMA node, falling back to cross-NUMA if needed.
     * Returns a GPUAllocation with localGpuCount set to the number of NUMA-local GPUs allocated.
     */
    private Optional<Tuple2<GPUAllocation, Integer>> allocateGPUs(
            long requiredGpus,
            int preferredNumaNode,
            Set<String> freeGpuIds,
            Map<String, Integer> gpuNumaAffinities,
            Map<String, GPUInfo> gpuMetadata) {
        if (requiredGpus == 0) {
            return Optional.of(new Tuple2<>(new GPUAllocation(Set.of(), null), 0));
        }

        if (freeGpuIds.size() < requiredGpus) {
            return Optional.empty();
        }

        // Prefer GPUs on the same NUMA node as the CPU allocation
        val numaLocalGpus = freeGpuIds.stream()
                .sorted()
                .filter(gpuId -> Objects.equals(gpuNumaAffinities.get(gpuId), preferredNumaNode))
                .limit(requiredGpus)
                .collect(Collectors.toUnmodifiableSet());

        val resolvedGpuType = resolveGpuType(numaLocalGpus, gpuMetadata);

        if (numaLocalGpus.size() == requiredGpus) {
            // All allocated GPUs are NUMA-local
            return Optional.of(new Tuple2<>(new GPUAllocation(numaLocalGpus, resolvedGpuType), numaLocalGpus.size()));
        }

        // Not enough NUMA-local GPUs; fall back to any free GPUs
        // localGpuCount is still the number of NUMA-local GPUs we managed to allocate
        val allocatedGpus = freeGpuIds.stream()
                .sorted()
                .limit(requiredGpus)
                .collect(Collectors.toUnmodifiableSet());
        val resolvedGpuTypeFallback = resolveGpuType(allocatedGpus, gpuMetadata);
        return Optional.of(new Tuple2<>(new GPUAllocation(allocatedGpus, resolvedGpuTypeFallback), numaLocalGpus.size()));
    }

    private GpuType resolveGpuType(Set<String> allocatedGpuIds, Map<String, GPUInfo> gpuMetadata) {
        return allocatedGpuIds.stream()
                .map(gpuMetadata::get)
                .filter(Objects::nonNull)
                .findFirst()
                .map(info -> new GpuType(info.getGpuVendor(), info.getGpuModel()))
                .orElse(null);
    }

}

