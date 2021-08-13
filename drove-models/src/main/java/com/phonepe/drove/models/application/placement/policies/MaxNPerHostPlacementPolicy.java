package com.phonepe.drove.models.application.placement.policies;

import com.phonepe.drove.models.application.placement.PlacementPolicy;
import com.phonepe.drove.models.application.placement.PlacementPolicyType;
import com.phonepe.drove.models.application.placement.PlacementPolicyVisitor;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.Value;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;

/**
 *
 */
@Value
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class MaxNPerHostPlacementPolicy extends PlacementPolicy {
    @Min(1)
    @Max(32)
    int numContainers;

    public MaxNPerHostPlacementPolicy(int numContainers) {
        super(PlacementPolicyType.MAX_N_PER_HOST);
        this.numContainers = numContainers;
    }

    @Override
    public <T> T accept(PlacementPolicyVisitor<T> visitor) {
        return visitor.visit(this);
    }
}
