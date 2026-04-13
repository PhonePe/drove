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

package com.phonepe.drove.models.application.placement;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Type of placement policy for container scheduling
 */
@Schema(description = "Type of placement policy for container scheduling")
public enum PlacementPolicyType {
    @Schema(description = "Only one instance per host")
    ONE_PER_HOST,
    @Schema(description = "Maximum N instances per host")
    MAX_N_PER_HOST,
    @Schema(description = "Place only on hosts with a specific tag")
    MATCH_TAG,
    @Schema(description = "Place only on hosts without any tags")
    NO_TAG,
    @Schema(description = "Use custom rules (HOPE or MVEL) for placement decisions")
    RULE_BASED,
    @Schema(description = "Place on any available host")
    ANY,
    @Schema(description = "Combine multiple placement policies with AND/OR logic")
    COMPOSITE,
    @Schema(description = "Place on the local executor node")
    LOCAL
}
