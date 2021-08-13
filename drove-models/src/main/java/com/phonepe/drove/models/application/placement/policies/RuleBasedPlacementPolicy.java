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
public class RuleBasedPlacementPolicy extends PlacementPolicy {

    @NotEmpty
    String rule;

    public RuleBasedPlacementPolicy(String rule) {
        super(PlacementPolicyType.RULE_BASED);
        this.rule = rule;
    }

    @Override
    public <T> T accept(PlacementPolicyVisitor<T> visitor) {
        return visitor.visit(this);
    }
}
