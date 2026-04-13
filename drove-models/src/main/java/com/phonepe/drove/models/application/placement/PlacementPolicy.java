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

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.phonepe.drove.models.application.placement.policies.*;
import io.swagger.v3.oas.annotations.media.DiscriminatorMapping;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * Base class for placement policies that control container scheduling decisions
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXISTING_PROPERTY, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(name = "ONE_PER_HOST", value = OnePerHostPlacementPolicy.class),
        @JsonSubTypes.Type(name = "MAX_N_PER_HOST", value = MaxNPerHostPlacementPolicy.class),
        @JsonSubTypes.Type(name = "MATCH_TAG", value = MatchTagPlacementPolicy.class),
        @JsonSubTypes.Type(name = "NO_TAG", value = NoTagPlacementPolicy.class),
        @JsonSubTypes.Type(name = "RULE_BASED", value = RuleBasedPlacementPolicy.class),
        @JsonSubTypes.Type(name = "ANY", value = AnyPlacementPolicy.class),
        @JsonSubTypes.Type(name = "COMPOSITE", value = CompositePlacementPolicy.class),
        @JsonSubTypes.Type(name = "LOCAL", value = LocalPlacementPolicy.class),
})
@Data
@Schema(
    description = "Placement policy that controls how containers are scheduled on executor hosts. " +
                  "Different policy types provide various strategies for container placement.",
    discriminatorProperty = "type",
    discriminatorMapping = {
        @DiscriminatorMapping(value = "ONE_PER_HOST", schema = OnePerHostPlacementPolicy.class),
        @DiscriminatorMapping(value = "MAX_N_PER_HOST", schema = MaxNPerHostPlacementPolicy.class),
        @DiscriminatorMapping(value = "MATCH_TAG", schema = MatchTagPlacementPolicy.class),
        @DiscriminatorMapping(value = "NO_TAG", schema = NoTagPlacementPolicy.class),
        @DiscriminatorMapping(value = "RULE_BASED", schema = RuleBasedPlacementPolicy.class),
        @DiscriminatorMapping(value = "ANY", schema = AnyPlacementPolicy.class),
        @DiscriminatorMapping(value = "COMPOSITE", schema = CompositePlacementPolicy.class),
        @DiscriminatorMapping(value = "LOCAL", schema = LocalPlacementPolicy.class)
    },
    subTypes = {
        OnePerHostPlacementPolicy.class,
        MaxNPerHostPlacementPolicy.class,
        MatchTagPlacementPolicy.class,
        NoTagPlacementPolicy.class,
        RuleBasedPlacementPolicy.class,
        AnyPlacementPolicy.class,
        CompositePlacementPolicy.class,
        LocalPlacementPolicy.class
    }
)
public abstract class PlacementPolicy {
    @Schema(description = "Type of placement policy", requiredMode = Schema.RequiredMode.REQUIRED)
    private final PlacementPolicyType type;

    public abstract <T> T accept(final PlacementPolicyVisitor<T> visitor);
}
