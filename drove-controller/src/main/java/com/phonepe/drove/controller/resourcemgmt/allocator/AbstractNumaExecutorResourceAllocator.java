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

import com.phonepe.drove.controller.resourcemgmt.AllocatedExecutorNode;
import com.phonepe.drove.controller.resourcemgmt.ExecutorHostInfo;
import com.phonepe.drove.models.info.resources.allocation.CPUAllocation;
import com.phonepe.drove.models.info.resources.allocation.GPUAllocation;
import com.phonepe.drove.models.info.resources.allocation.MemoryAllocation;
import lombok.val;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Base NUMA selector that encapsulates CPU and memory allocation helpers.
 */
public abstract class AbstractNumaExecutorResourceAllocator implements ExecutorResourceAllocator {

    protected List<Integer> candidateNumaNodeIds(
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

    protected long freeCoresForNode(ExecutorHostInfo.NumaNodeInfo node) {
        return node.getCores()
                .entrySet()
                .stream()
                .filter(e -> e.getValue().equals(ExecutorHostInfo.CoreState.FREE))
                .count();
    }

    protected AllocatedExecutorNode buildAllocatedNode(
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

    protected MemoryAllocation allocateMemory(int numaNodeId, long requiredMemory) {
        return new MemoryAllocation(Map.of(numaNodeId, requiredMemory));
    }

    protected CPUAllocation allocateCPUs(
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
}

