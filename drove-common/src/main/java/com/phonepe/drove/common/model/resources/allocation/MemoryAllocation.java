package com.phonepe.drove.common.model.resources.allocation;

import com.phonepe.drove.models.application.requirements.ResourceType;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

import java.util.Map;

/**
 *
 */
@Value
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Jacksonized
@Builder
public class MemoryAllocation extends ResourceAllocation {
    Map<Integer, Long> memoryInMB;

    public MemoryAllocation(Map<Integer, Long> memoryInMB) {
        super(ResourceType.MEMORY);
        this.memoryInMB = memoryInMB;
    }

    @Override
    public <T> T accept(ResourceAllocationVisitor<T> visitor) {
        return visitor.visit(this);
    }
}
