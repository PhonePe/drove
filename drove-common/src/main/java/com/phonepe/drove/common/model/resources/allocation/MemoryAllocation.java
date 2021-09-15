package com.phonepe.drove.common.model.resources.allocation;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.phonepe.drove.models.application.requirements.ResourceType;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.Value;

/**
 *
 */
@Value
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class MemoryAllocation extends ResourceAllocation {
    long memoryInMB;

    public MemoryAllocation(@JsonProperty("memoryInMB") long memoryInMB) {
        super(ResourceType.MEMORY);
        this.memoryInMB = memoryInMB;
    }

    @Override
    public <T> T accept(ResourceAllocationVisitor<T> visitor) {
        return visitor.visit(this);
    }
}
