/*
 * Copyright (c) 2026 Original Author(s), PhonePe India Pvt. Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.phonepe.drove.models.api;

import javax.validation.Valid;
import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;

import com.phonepe.drove.models.application.placement.PlacementPolicy;
import com.phonepe.drove.models.interfaces.DeploymentSpec;

import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

@Value
@Builder
@Jacksonized
public class PlacementSimulationRequest {
    @NotNull
    @Valid
    DeploymentSpec spec;

    @Valid
    PlacementPolicy placementPolicy;

    @Min(value = 1, message = "- numInstances must be at least 1")
    @Max(value = 2048, message = "- numInstances must be at most 2048")
    int numInstances;
}
