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

import com.phonepe.drove.controller.utils.ControllerUtils;
import com.phonepe.drove.models.application.placement.PlacementPolicy;
import com.phonepe.drove.models.info.nodedata.ExecutorState;
import com.phonepe.drove.models.info.resources.allocation.AllocatedExecutorNode;
import com.phonepe.drove.models.interfaces.DeploymentSpec;

import java.util.EnumSet;

import java.util.Optional;
import java.util.Set;

/**
 *
 */
public interface InstanceScheduler {
    default Optional<AllocatedExecutorNode> schedule(
            final String schedulingSessionId,
            final String instanceId,
            final DeploymentSpec deploymentSpec) {
        var placementPolicy = ControllerUtils.computeEffectivePlacementPolicy(deploymentSpec);
        return schedule(schedulingSessionId, instanceId, deploymentSpec, placementPolicy);
    }

    default Optional<AllocatedExecutorNode> schedule(
            final String schedulingSessionId,
            final String instanceId,
            final DeploymentSpec deploymentSpec,
            final PlacementPolicy placementPolicy) {
        return schedule(schedulingSessionId,
                        instanceId,
                        deploymentSpec,
                        placementPolicy,
                        EnumSet.of(ExecutorState.ACTIVE));
    }

    Optional<AllocatedExecutorNode> schedule(
            final String schedulingSessionId,
            final String instanceId,
            final DeploymentSpec applicationSpec,
            final PlacementPolicy placementPolicy,
            final Set<ExecutorState> allowedStates);

    /**
     * Simulates placement of {@code numInstances} instances of the given spec on
     * the cluster without locking any resources. Reports how many instances the
     * cluster can place, and errors when it cannot place all of them. The
     * placements list is populated only when {@code detail} is set. Capacity data
     * is as fresh as the last executor refresh; actual scheduling can differ.
     */
    PlacementCheckResult checkPlacement(final DeploymentSpec deploymentSpec,
                                        final PlacementPolicy requiredPlacementPolicy,
                                        final int numInstances,
                                        final boolean detail);

    default PlacementCheckResult checkPlacement(final DeploymentSpec deploymentSpec, final int numInstances) {
        return checkPlacement(deploymentSpec, null, numInstances, false);
    }

    void finaliseSession(String schedulingSessionId);


    boolean discardAllocation(String schedulingSessionId, String instanceId, final AllocatedExecutorNode node);
}
