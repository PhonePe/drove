package com.phonepe.drove.models.application.placement.policies;

import com.phonepe.drove.models.application.placement.PlacementPolicy;
import com.phonepe.drove.models.application.placement.PlacementPolicyType;
import com.phonepe.drove.models.application.placement.PlacementPolicyVisitor;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.Value;

import javax.validation.constraints.NotEmpty;

/**
 *
 */
@Value
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class MatchTagPlacementPolicy extends PlacementPolicy {

    @NotEmpty
    String tag;

    public MatchTagPlacementPolicy(String tag) {
        super(PlacementPolicyType.MATCH_TAG);
        this.tag = tag;
    }

    @Override
    public <T> T accept(PlacementPolicyVisitor<T> visitor) {
        return visitor.visit(this);
    }
}
