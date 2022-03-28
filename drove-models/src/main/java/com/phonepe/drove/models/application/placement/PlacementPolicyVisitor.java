package com.phonepe.drove.models.application.placement;

import com.phonepe.drove.models.application.placement.policies.*;

/**
 *
 */
public interface PlacementPolicyVisitor<T> {
    T visit(OnePerHostPlacementPolicy onePerHost);

    T visit(MaxNPerHostPlacementPolicy maxNPerHost);

    T visit(MatchTagPlacementPolicy matchTag);

    T visit(RuleBasedPlacementPolicy ruleBased);

    T visit(AnyPlacementPolicy anyPlacementPolicy);

    T visit(CompositePlacementPolicy compositePlacementPolicy);
}
