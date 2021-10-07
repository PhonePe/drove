package com.phonepe.drove.common.model.resources.allocation;

import com.phonepe.drove.models.application.requirements.ResourceType;
import lombok.*;
import lombok.extern.jackson.Jacksonized;

import java.util.Map;
import java.util.Set;

/**
 *
 */
@Value
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Jacksonized
@Builder
public class CPUAllocation extends ResourceAllocation {
    Map<Integer, Set<Integer>> cores;

    public CPUAllocation(Map<Integer, Set<Integer>> cores) {
        super(ResourceType.CPU);
        this.cores = cores;
    }

    @Override
    public <T> T accept(ResourceAllocationVisitor<T> visitor) {
        return visitor.visit(this);
    }
}
