package com.phonepe.drove.models.application.placement.policies;

import com.phonepe.drove.models.application.placement.PlacementPolicy;
import com.phonepe.drove.models.application.placement.PlacementPolicyType;
import com.phonepe.drove.models.application.placement.PlacementPolicyVisitor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

import javax.validation.constraints.NotEmpty;
import java.util.List;

/**
 *
 */
@Value
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Jacksonized
@Builder
public class CompositePlacementPolicy extends PlacementPolicy {

    public enum CombinerType {
        AND,
        OR
    }

    @NotEmpty
    List<PlacementPolicy> policies;

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
