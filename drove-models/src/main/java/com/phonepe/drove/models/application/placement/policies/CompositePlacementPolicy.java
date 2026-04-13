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

package com.phonepe.drove.models.application.placement.policies;

import com.phonepe.drove.models.application.placement.PlacementPolicy;
import com.phonepe.drove.models.application.placement.PlacementPolicyType;
import com.phonepe.drove.models.application.placement.PlacementPolicyVisitor;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

import javax.validation.constraints.NotEmpty;
import java.util.List;

/**
 * Placement policy that combines multiple policies with logical operators
 */
@Value
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Jacksonized
@Builder
@Schema(description = "Placement policy that combines multiple placement policies using AND/OR logical operators")
public class CompositePlacementPolicy extends PlacementPolicy {

    @Schema(description = "Logical operator for combining multiple placement policies")
    public enum CombinerType {
        @Schema(description = "All policies must be satisfied")
        AND,
        @Schema(description = "At least one policy must be satisfied")
        OR
    }

    @NotEmpty(message = "- Specify one or more policies to combine")
    @Schema(description = "List of placement policies to combine", requiredMode = Schema.RequiredMode.REQUIRED)
    List<PlacementPolicy> policies;

    @Schema(description = "Logical operator to combine policies (AND requires all, OR requires at least one)", example = "AND")
    CombinerType combiner;

    public CompositePlacementPolicy(
            List<PlacementPolicy> policies,
            CombinerType combiner) {
        super(PlacementPolicyType.COMPOSITE);
        this.policies = policies;
        this.combiner = combiner;
    }

    @Override
    public <T> T accept(PlacementPolicyVisitor<T> visitor) {
        return visitor.visit(this);
    }
}
