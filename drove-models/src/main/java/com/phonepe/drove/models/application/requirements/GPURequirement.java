package com.phonepe.drove.models.application.requirements;

import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

@Value
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Jacksonized
@Builder
public class GPURequirement extends ResourceRequirement{
    long count;

    public GPURequirement(long count) {
        super(ResourceType.GPU);
        this.count = count;
    }

    @Override
    public <T> T accept(ResourceRequirementVisitor<T> visitor) {
        return visitor.visit(this);
    }
}
