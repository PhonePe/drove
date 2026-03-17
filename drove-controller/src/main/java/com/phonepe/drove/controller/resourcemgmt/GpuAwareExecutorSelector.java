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
import com.phonepe.drove.models.info.GPUInfo;
import com.phonepe.drove.models.info.nodedata.ExecutorNodeData;
import com.phonepe.drove.models.info.resources.PhysicalLayout;
import lombok.val;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Chooses the feasible executor whose allocation has the most NUMA-local GPUs.
 * Ties retain the incoming node order.
 */
public class GpuAwareExecutorSelector implements ExecutorSelector {

    @Override
    public Optional<AllocatedExecutorNode> select(
            List<ExecutorHostInfo> nodes,
            Predicate<ExecutorHostInfo> nodeFilter,
            Function<ExecutorHostInfo, Optional<AllocatedExecutorNode>> allocator,
            Predicate<AllocatedExecutorNode> allocationFilter) {
        return nodes.stream()
                .filter(nodeFilter)
                .map(node -> allocator.apply(node)
                        .filter(allocationFilter)
                        .map(allocation -> new ScoredAllocation(allocation, localGpuCount(node, allocation))))
                .flatMap(Optional::stream)
                .max((a, b) -> Long.compare(a.localGpuCount(), b.localGpuCount()))
                .map(ScoredAllocation::allocation);
    }

    private long localGpuCount(ExecutorHostInfo hostInfo, AllocatedExecutorNode allocation) {
        if (allocation.getCpu().getCores().isEmpty()) {
            return 0;
        }

        val selectedNuma = allocation.getCpu().getCores().keySet().iterator().next();
        val gpuNumaAffinities = getGpuNumaAffinities(hostInfo);
        return allocation.getGpu().getGpuIds().stream()
                .map(gpuNumaAffinities::get)
                .filter(numa -> Objects.equals(numa, selectedNuma))
                .count();
    }

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

    private record ScoredAllocation(AllocatedExecutorNode allocation, long localGpuCount) {
    }
}

