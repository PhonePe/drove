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

package com.phonepe.drove.controller.resourcemgmt;

import com.phonepe.drove.models.info.ExecutorResourceSnapshot;
import com.phonepe.drove.models.info.resources.allocation.GPUAllocation;
import com.phonepe.drove.models.info.GPUInfo;
import com.phonepe.drove.models.info.nodedata.ExecutorNodeData;
import com.phonepe.drove.models.info.resources.PhysicalLayout;
import lombok.val;

import java.util.*;
import java.util.stream.Collectors;

/**
 * NUMA node selector that prioritizes nodes with the most local free GPUs.
 * This ensures that when GPUs are requested, the selected CPU/memory NUMA node
 * is the one that already has enough local GPUs, avoiding unnecessary cross-NUMA GPU allocation.
 */
public class GpuAwareNumaExecutorResourceAllocator extends AbstractNumaExecutorResourceAllocator {

    @Override
    public Optional<AllocatedExecutorNode> allocate(
            ExecutorHostInfo hostInfo,
            long requiredCpus,
            long requiredMemory,
            long requiredGpus) {
        val candidateNumaNodeIds = candidateNumaNodeIds(hostInfo, requiredCpus, requiredMemory);
        if (candidateNumaNodeIds.isEmpty()) {
            return Optional.empty();
        }

        if(requiredGpus == 0) {
            return Optional.of(buildAllocatedNode(
                    hostInfo,
                    candidateNumaNodeIds.get(0),
                    requiredCpus,
                    requiredMemory,
                    new GPUAllocation(Set.of())));
        }

        val totalFreeGpus = hostInfo.getGpuInfo().getGpus().values().stream()
                .filter(state -> state.equals(ExecutorHostInfo.CoreState.FREE))
                .count();
        if (totalFreeGpus < requiredGpus) {
            return Optional.empty();
        }

        val gpuNumaAffinities = getGpuNumaAffinities(hostInfo);

        val freeGpuIds = getFreeGpuIds(hostInfo);
        val localFreeGpuCountByNuma = freeGpuIds.stream()
                .filter(gpuNumaAffinities::containsKey)
                .collect(Collectors.groupingBy(gpuNumaAffinities::get, Collectors.counting()));

        val sortedCandidates = candidateNumaNodeIds.stream()
                .sorted(Comparator.comparingLong((Integer numaId) -> localFreeGpuCountByNuma.getOrDefault(numaId, 0L))
                                .reversed()
                                .thenComparingInt(numaId -> numaId))
                .toList();

        for (Integer selectedNumaNodeId : sortedCandidates) {
            val gpuAlloc = allocateGPUs(requiredGpus, selectedNumaNodeId, freeGpuIds, gpuNumaAffinities);
            if (gpuAlloc.isEmpty()) {
                continue;
            }

            return Optional.of(buildAllocatedNode(
                    hostInfo,
                    selectedNumaNodeId,
                    requiredCpus,
                    requiredMemory,
                    gpuAlloc.get()));
        }

        return Optional.empty();
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
     * Get the set of free GPU IDs across all NUMA nodes.
     */
    private Set<String> getFreeGpuIds(ExecutorHostInfo hostInfo) {
        return hostInfo.getGpuInfo().getGpus().entrySet().stream()
                .filter(entry -> entry.getValue().equals(ExecutorHostInfo.CoreState.FREE))
                .map(Map.Entry::getKey)
                .collect(Collectors.toUnmodifiableSet());
    }

    /**
     * Allocate GPUs preferring those local to the selected NUMA node, falling back to cross-NUMA if needed.
     */
    private Optional<GPUAllocation> allocateGPUs(
            long requiredGpus,
            int preferredNumaNode,
            Set<String> freeGpuIds,
            Map<String, Integer> gpuNumaAffinities) {
        if (requiredGpus == 0) {
            return Optional.of(new GPUAllocation(Set.of()));
        }

        if (freeGpuIds.size() < requiredGpus) {
            return Optional.empty();
        }

        // Prefer GPUs on the same NUMA node as the CPU allocation
        val numaLocalGpus = freeGpuIds.stream()
                .filter(gpuId -> Objects.equals(gpuNumaAffinities.get(gpuId), preferredNumaNode))
                .limit(requiredGpus)
                .collect(Collectors.toUnmodifiableSet());

        if (numaLocalGpus.size() == requiredGpus) {
            return Optional.of(new GPUAllocation(numaLocalGpus));
        }

        // Not enough NUMA-local GPUs; fall back to any free GPUs
        return Optional.of(new GPUAllocation(freeGpuIds.stream()
                                                     .limit(requiredGpus)
                                                     .collect(Collectors.toUnmodifiableSet())));
    }

}


