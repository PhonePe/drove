package com.phonepe.drove.models.application.placement.policies;

import com.phonepe.drove.models.application.placement.PlacementPolicy;
import com.phonepe.drove.models.application.placement.PlacementPolicyType;
import com.phonepe.drove.models.application.placement.PlacementPolicyVisitor;

/**
 *
 */
public class AnyPlacementPolicy extends PlacementPolicy {
    public AnyPlacementPolicy() {
        super(PlacementPolicyType.ANY);
    }

    @Override
    public <T> T accept(PlacementPolicyVisitor<T> visitor) {
        return visitor.visit(this);
    }
}
