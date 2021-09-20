package com.phonepe.drove.common.model.resources.available;

import com.phonepe.drove.models.application.requirements.ResourceType;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.Value;

import java.util.Map;

/**
 *
 */
@Value
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class AvailableMemory extends AvailableResource {
    Map<Integer, Long> memoryInMB;

    public AvailableMemory(Map<Integer, Long> memoryInMB) {
        super(ResourceType.MEMORY);
        this.memoryInMB = memoryInMB;
    }

    @Override
    public <T> T accept(AvailableResourceVisitor<T> visitor) {
        return visitor.visit(this);
    }
}
