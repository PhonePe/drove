package com.phonepe.drove.models.info.resources.allocation;

import com.google.common.base.Preconditions;
import com.phonepe.drove.models.application.requirements.ResourceType;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

import java.util.Set;
@Value
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Jacksonized
@Builder
public class GPUAllocation extends ResourceAllocation{
    Set<String> gpuIds;

    public GPUAllocation(Set<String> gpuIds) {
        super(ResourceType.GPU);
        this.gpuIds = gpuIds;
    }

    @Override
    public <T> T accept(ResourceAllocationVisitor<T> visitor) {
        return visitor.visit(this);
    }
}
