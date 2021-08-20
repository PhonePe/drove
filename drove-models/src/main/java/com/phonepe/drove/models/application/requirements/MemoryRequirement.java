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
public class MemoryRequirement extends ResourceRequirement {
    long sizeInMB;

    public MemoryRequirement(int sizeInMB) {
        super(ResourceRequirementType.MEMORY);
        this.sizeInMB = sizeInMB;
    }

    @Override
    public <T> T accept(ResourceRequirementVisitor<T> visitor) {
        return visitor.visit(this);
    }
}
