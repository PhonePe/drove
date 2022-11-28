package com.phonepe.drove.models.application.placement;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.phonepe.drove.models.application.placement.policies.*;
import lombok.Data;

/**
 *
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
})
@Data
public abstract class PlacementPolicy {
    private final PlacementPolicyType type;

    public abstract <T> T accept(final PlacementPolicyVisitor<T> visitor);
}
