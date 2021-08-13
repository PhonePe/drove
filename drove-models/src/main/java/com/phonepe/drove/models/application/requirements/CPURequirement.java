package com.phonepe.drove.models.application.requirements;

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
    int count;

    public CPURequirement(int count) {
        super(ResourceRequirementType.CPU);
        this.count = count;
    }

    @Override
    public <T> T accept(ResourceRequirementVisitor<T> visitor) {
        return visitor.visit(this);
    }
}
