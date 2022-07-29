package com.phonepe.drove.models.application.placement.policies;

import com.phonepe.drove.models.application.placement.PlacementPolicy;
import com.phonepe.drove.models.application.placement.PlacementPolicyType;
import com.phonepe.drove.models.application.placement.PlacementPolicyVisitor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;

/**
 *
 */
@Value
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Jacksonized
@Builder
public class MaxNPerHostPlacementPolicy extends PlacementPolicy {
    @Min(value = 1, message = "- Min one hose needs to be specified")
    @Max(value = 64, message = "- Maximum 64 containers can be specified per host")
    int max;

    public MaxNPerHostPlacementPolicy(int max) {
        super(PlacementPolicyType.MAX_N_PER_HOST);
        this.max = max;
    }

    @Override
    public <T> T accept(PlacementPolicyVisitor<T> visitor) {
        return visitor.visit(this);
    }
}
