package com.phonepe.drove.models.info.resources.allocation;

import com.phonepe.drove.models.application.requirements.GpuType;
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
    GpuType gpuType;

    public GPUAllocation(Set<String> gpuIds, GpuType gpuType) {
        super(ResourceType.GPU);
        this.gpuIds = gpuIds;
        this.gpuType = gpuType;
    }

    @Override
    public <T> T accept(ResourceAllocationVisitor<T> visitor) {
        return visitor.visit(this);
    }
}
