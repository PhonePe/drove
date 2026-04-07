package com.phonepe.drove.models.application.requirements;

import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

import java.util.Objects;

@Value
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Jacksonized
@Builder
public class GPURequirement extends ResourceRequirement{
    long count;
    GpuType gpuType;

    public GPURequirement(long count, GpuType gpuType) {
        super(ResourceType.GPU);
        this.count = count;
        this.gpuType = Objects.requireNonNull(gpuType, "GPU type is mandatory");
    }

    @Override
    public <T> T accept(ResourceRequirementVisitor<T> visitor) {
        return visitor.visit(this);
    }
}
