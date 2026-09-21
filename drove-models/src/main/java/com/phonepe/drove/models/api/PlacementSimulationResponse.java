/*
 *  Copyright (c) 2026 Original Author(s), PhonePe India Pvt. Ltd.
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

package com.phonepe.drove.models.api;

import com.phonepe.drove.models.info.resources.allocation.AllocatedExecutorNode;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

import java.util.List;

/**
 * Result of a placement simulation. Reports the requested instance count, the
 * count the cluster can place, and placement errors. The placements list holds
 * the simulated allocations and is populated only when the client asks for
 * details.
 */
@Value
@Builder
@Jacksonized
public class PlacementSimulationResponse {
    int requested;
    int placed;
    List<String> errors;
    List<AllocatedExecutorNode> placements;
}
