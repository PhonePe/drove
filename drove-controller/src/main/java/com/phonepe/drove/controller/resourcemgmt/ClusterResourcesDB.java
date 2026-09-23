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

import com.phonepe.drove.controller.managed.ExecutorTopologyChanges;
import com.phonepe.drove.models.application.requirements.ResourceRequirement;
import com.phonepe.drove.models.info.ExecutorResourceSnapshot;
import com.phonepe.drove.models.info.nodedata.ExecutorNodeData;
import com.phonepe.drove.models.info.nodedata.ExecutorState;
import com.phonepe.drove.models.info.resources.allocation.AllocatedExecutorNode;
import com.phonepe.drove.models.info.resources.allocation.CPUAllocation;
import com.phonepe.drove.models.info.resources.allocation.MemoryAllocation;
import io.appform.functionmetrics.MonitoredFunction;
import io.appform.signals.signals.ConsumingFireForgetSignal;
import lombok.Value;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Predicate;

/**
 *
 */
public abstract class ClusterResourcesDB {

    @Value
    public static class ClusterResourcesSummary {
        int numExecutors;
        int freeCores;
        int usedCores;
        int totalCores;
        long freeMemory;
        long usedMemory;
        long totalMemory;
        int freeGpus;
        int usedGpus;
        int totalGpus;
        Map<String, Integer> gpuVendorCounts;
    }

    @Value
    public static class FilterContext {
        List<ExecutorHostInfo> liveExecutors;
        ExecutorNodeData allocatedExecutorNodeData;
        AllocatedExecutorNode allocatedExecutorNode;
        String instanceId;
    }

    protected final ConsumingFireForgetSignal<ExecutorTopologyChanges> topologyChanged = new ConsumingFireForgetSignal<>();

    public ConsumingFireForgetSignal<ExecutorTopologyChanges> onTopologyChange() {
        return topologyChanged;
    }

    @MonitoredFunction
    public abstract long executorCount(boolean skipOffDutyNodes);

    public abstract List<ExecutorHostInfo> currentSnapshot(boolean skipOffDutyNodes);

    @MonitoredFunction
    public abstract List<ExecutorHostInfo> lastKnownSnapshots();

    public abstract Optional<ExecutorHostInfo> currentSnapshot(String executorId);

    public abstract Map<String, ExecutorHostInfo> currentSnapshot(Set<String> executorIds);

    public abstract Optional<ExecutorHostInfo> lastKnownSnapshot(String executorId);

    public abstract void remove(Collection<String> executorIds);

    public abstract void update(final List<ExecutorNodeData> nodeData);

    public abstract void update(ExecutorResourceSnapshot snapshot);

    /**
     * Makes a copy of the current resource state. Changes to the copy do not
     * change this DB. Use the copy for placement simulation.
     */
    public abstract ClusterResourcesDB copy();

    /**
     * Selects a node for a single instance. The FilterContext instance id is
     * null. Callers that need the instance id in the filter must use the
     * batched overload with an explicit instance id.
     */
    public Optional<AllocatedExecutorNode> selectNodes(
            List<ResourceRequirement> requirements,
            Set<ExecutorState> allowedExecutorState,
            Predicate<FilterContext> filter,
            boolean shouldShuffle) {
        return selectNodes(requirements,
                           allowedExecutorState,
                           filter,
                           shouldShuffle,
                           Collections.singletonList(null),
                           null)
                .stream()
                .findFirst();
    }

    /**
     * Selects nodes for the given instance ids under a single lock acquisition.
     * Selection stops at the first instance that cannot be placed. The
     * onAllocation callback runs once per selected node, in instance id order,
     * before the selection of the next instance. The filter can read the
     * current instance id from FilterContext.
     */
    public abstract List<AllocatedExecutorNode> selectNodes(
            List<ResourceRequirement> requirements,
            Set<ExecutorState> allowedExecutorState,
            Predicate<FilterContext> filter,
            boolean shouldShuffle,
            List<String> instanceIds,
            BiConsumer<String, AllocatedExecutorNode> onAllocation);

    public abstract void deselectNode(String executorId,
                                             CPUAllocation cpuAllocation,
                                             MemoryAllocation memoryAllocation) ;

    public abstract boolean isBlacklisted(String executorId);

    @MonitoredFunction
    public abstract boolean isActive(String executorId);

    public abstract Set<String> blacklistedNodes();

    protected final void raiseEvent(
            Set<String> addedExecutors,
            Set<String> removedExecutors,
            Set<String> currentLive) {
        if(!addedExecutors.isEmpty() || !removedExecutors.isEmpty()) {
            topologyChanged.dispatch(new ExecutorTopologyChanges(addedExecutors, removedExecutors, currentLive));
        }
    }

}
