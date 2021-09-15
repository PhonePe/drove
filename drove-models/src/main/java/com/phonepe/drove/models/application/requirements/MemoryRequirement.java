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
public class MemoryRequirement extends ResourceRequirement {
    long sizeInMB;

    public MemoryRequirement(@JsonProperty("sizeInMB") long sizeInMB) {
        super(ResourceType.MEMORY);
        this.sizeInMB = sizeInMB;
    }

    @Override
    public <T> T accept(ResourceRequirementVisitor<T> visitor) {
        return visitor.visit(this);
    }
}
