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

import com.phonepe.drove.models.info.resources.allocation.AllocatedExecutorNode;
import lombok.Builder;
import lombok.Value;

import java.util.List;

/**
 * Result of a placement check. Reports how many of the requested instances
 * the cluster can place, and errors when the cluster cannot place all of them.
 * The placements list holds the simulated allocations and is populated only
 * when the caller asks for details. The check is a simulation. It does not
 * lock resources. Capacity data is as fresh as the last executor refresh.
 * Actual scheduling can differ.
 */
@Value
public class PlacementCheckResult {
    int requested;
    int placed;
    List<String> errors;
    List<AllocatedExecutorNode> placements;

    @Builder
    public PlacementCheckResult(int requested, int placed, List<String> errors, List<AllocatedExecutorNode> placements) {
        this.requested = requested;
        this.placed = placed;
        this.errors = List.copyOf(errors);
        this.placements = null == placements ? List.of() : List.copyOf(placements);
    }

    public boolean fits() {
        return placed == requested;
    }
}

