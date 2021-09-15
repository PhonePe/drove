package com.phonepe.drove.common.model.resources.available;

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
public class AvailableMemory extends AvailableResource {
    long memoryInMB;

    public AvailableMemory(long memoryInMB) {
        super(ResourceType.MEMORY);
        this.memoryInMB = memoryInMB;
    }

    @Override
    public <T> T accept(AvailableResourceVisitor<T> visitor) {
        return visitor.visit(this);
    }
}
