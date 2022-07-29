package com.phonepe.drove.models.application.requirements;

import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

/**
 *
 */
@Value
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Jacksonized
@Builder
public class MemoryRequirement extends ResourceRequirement {
    long sizeInMB;

    public MemoryRequirement(long sizeInMB) {
        super(ResourceType.MEMORY);
        this.sizeInMB = sizeInMB;
    }

    @Override
    public <T> T accept(ResourceRequirementVisitor<T> visitor) {
        return visitor.visit(this);
    }
}
