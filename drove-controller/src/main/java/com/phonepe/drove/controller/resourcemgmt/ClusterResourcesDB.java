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
import com.phonepe.drove.models.info.resources.allocation.CPUAllocation;
import com.phonepe.drove.models.info.resources.allocation.MemoryAllocation;
import io.appform.functionmetrics.MonitoredFunction;
import io.appform.signals.signals.ConsumingFireForgetSignal;
import lombok.Value;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
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

    public abstract Optional<ExecutorHostInfo> lastKnownSnapshot(String executorId);

    public abstract void remove(Collection<String> executorIds);

    public abstract void update(final List<ExecutorNodeData> nodeData);

    public abstract void update(ExecutorResourceSnapshot snapshot);

    public abstract Optional<AllocatedExecutorNode> selectNodes(
            List<ResourceRequirement> requirements, Predicate<AllocatedExecutorNode> filter);

    public abstract void deselectNode(String executorId,
                                             CPUAllocation cpuAllocation,
                                             MemoryAllocation memoryAllocation) ;

    public abstract boolean isBlacklisted(String executorId);

    public abstract void markBlacklisted(String executorId);

    public abstract void unmarkBlacklisted(String executorId);

    protected final void raiseEvent(
            Set<String> addedExecutors,
            Set<String> removedExecutors,
            Set<String> currentKnown) {
        if(!addedExecutors.isEmpty() || !removedExecutors.isEmpty()) {
            topologyChanged.dispatch(new ExecutorTopologyChanges(addedExecutors, removedExecutors, currentKnown));
        }
    }

}
