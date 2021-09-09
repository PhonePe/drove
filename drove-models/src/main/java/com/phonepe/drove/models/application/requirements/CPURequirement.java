package com.phonepe.drove.models.application.requirements;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.Value;

/**
 *
 */
@Value
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class CPURequirement extends ResourceRequirement {
    long count;

    public CPURequirement(@JsonProperty("count") long count) {
        super(ResourceRequirementType.CPU);
        this.count = count;
    }

    @Override
    public <T> T accept(ResourceRequirementVisitor<T> visitor) {
        return visitor.visit(this);
    }
}
