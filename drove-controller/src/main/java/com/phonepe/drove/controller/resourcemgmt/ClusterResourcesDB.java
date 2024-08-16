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

import com.phonepe.drove.models.application.requirements.ResourceRequirement;
import com.phonepe.drove.models.info.ExecutorResourceSnapshot;
import com.phonepe.drove.models.info.nodedata.ExecutorNodeData;
import io.appform.functionmetrics.MonitoredFunction;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

/**
 *
 */
public interface ClusterResourcesDB {
    List<ExecutorHostInfo> currentSnapshot(boolean skipOffDutyNodes);

    @MonitoredFunction
    List<ExecutorHostInfo> lastKnownSnapshots();

    Optional<ExecutorHostInfo> currentSnapshot(String executorId);

    Optional<ExecutorHostInfo> lastKnownSnapshot(String executorId);

    void remove(Collection<String> executorIds);

    void update(final List<ExecutorNodeData> nodeData);

    void update(ExecutorResourceSnapshot snapshot);

    Optional<AllocatedExecutorNode> selectNodes(
            List<ResourceRequirement> requirements, Predicate<AllocatedExecutorNode> filter);

    void deselectNode(final AllocatedExecutorNode executorNode);

    boolean isBlacklisted(String executorId);

    void markBlacklisted(String executorId);

    void unmarkBlacklisted(String executorId);

}
