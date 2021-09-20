package com.phonepe.drove.common.model.resources.allocation;

import com.phonepe.drove.models.application.requirements.ResourceType;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.Value;

import java.util.Set;

/**
 *
 */
@Value
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class MemoryAllocation extends ResourceAllocation {
    Set<Integer> nodes;
    long memoryInMB;

    public MemoryAllocation(Set<Integer> nodes, long memoryInMB) {
        super(ResourceType.MEMORY);
        this.nodes = nodes;
        this.memoryInMB = memoryInMB;
    }

    @Override
    public <T> T accept(ResourceAllocationVisitor<T> visitor) {
        return visitor.visit(this);
    }
}
