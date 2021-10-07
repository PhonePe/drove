package com.phonepe.drove.models.application.requirements;

import lombok.*;
import lombok.extern.jackson.Jacksonized;

/**
 *
 */
@Value
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Jacksonized
@Builder
public class CPURequirement extends ResourceRequirement {
    long count;

    public CPURequirement(long count) {
        super(ResourceType.CPU);
        this.count = count;
    }

    @Override
    public <T> T accept(ResourceRequirementVisitor<T> visitor) {
        return visitor.visit(this);
    }
}
