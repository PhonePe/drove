package com.phonepe.drove.models.application.placement.policies;

import com.phonepe.drove.models.application.placement.PlacementPolicy;
import com.phonepe.drove.models.application.placement.PlacementPolicyType;
import com.phonepe.drove.models.application.placement.PlacementPolicyVisitor;

/**
 *
 */
public class OnePerHostPlacementPolicy extends PlacementPolicy {
    public OnePerHostPlacementPolicy() {
        super(PlacementPolicyType.ONE_PER_HOST);
    }

    @Override
    public <T> T accept(PlacementPolicyVisitor<T> visitor) {
        return visitor.visit(this);
    }
}
