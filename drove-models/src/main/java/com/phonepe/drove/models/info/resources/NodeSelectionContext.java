/*
 * Copyright (c) 2025 Original Author(s), PhonePe India Pvt. Ltd.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  You may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */

package com.phonepe.drove.models.info.resources;

import lombok.Builder;
import lombok.NonNull;
import lombok.Value;
import lombok.With;
import lombok.extern.jackson.Jacksonized;

import java.util.Map;

import com.phonepe.drove.models.info.nodedata.ExecutorNodeData;
import com.phonepe.drove.models.info.resources.allocation.AllocatedExecutorNode;
import com.phonepe.drove.models.info.resources.allocation.InstanceResourceAllocation;
import com.phonepe.drove.models.interfaces.DeploymentSpec;

@Value
@Jacksonized
@Builder
@With
public class NodeSelectionContext {

    @Value
    @Builder
    @With
    public static class SchedulingSession {

        /**
         * The session ID for this scheduling session.
         * All allocation requests for this operation will have the same id.
         */
         String sessionId;

         /**
          * The current allocation state for the session.
          * This is a map of executor IDs vs the current allocation for that executor.
          */
         @Builder.Default
         Map<String, Map<String, InstanceResourceAllocation>> existingAllocations = Map.of();
    }

    @Value
    @Builder
    @With
    public static class SchedulingEnvironment {
        /**
         * The list of live executors in the cluster.
         */
        Map<String, ExecutorNodeData> liveExecutors;

        /**
         * Available environment variable as passed into the session.
         */
        Map<String, String> variables;
    }

    @Value
    @Builder
    @With
    public static class NodeAllocation {
        /**
         * The deployment spec for which the allocation is being made.
         */
        @NonNull
        DeploymentSpec spec;

        /**
         * The instance ID for which the allocation is being made.
         */
        @NonNull
        String instanceId;

        /**
         * The allocated node that needs to be evaluated
         */
        @NonNull
        AllocatedExecutorNode node;
    }

    /**
     * A view of the current scheduling session.
     */
    @NonNull
    SchedulingSession session;

    /**
     * A view of the current scheduling environment.
     */
    @NonNull
    SchedulingEnvironment environment;

    /**
     * A view of the current node allocation being evaluated.
     */
    @NonNull
    NodeAllocation allocation;
}
