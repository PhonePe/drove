package com.phonepe.drove.common.model.resources.allocation;

import com.fasterxml.jackson.annotation.JsonProperty;
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
public class CPUAllocation extends ResourceAllocation {
    Set<Integer> cores;

    public CPUAllocation(@JsonProperty("cores") Set<Integer> cores) {
        super(ResourceType.CPU);
        this.cores = cores;
    }

    @Override
    public <T> T accept(ResourceAllocationVisitor<T> visitor) {
        return visitor.visit(this);
    }
}
