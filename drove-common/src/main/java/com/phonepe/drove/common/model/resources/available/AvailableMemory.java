package com.phonepe.drove.common.model.resources.available;

import com.phonepe.drove.models.application.requirements.ResourceType;
import lombok.*;
import lombok.extern.jackson.Jacksonized;

import java.util.Map;

/**
 *
 */
@Value
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Jacksonized
@Builder
public class AvailableMemory extends AvailableResource {
    Map<Integer, Long> freeMemory;
    Map<Integer, Long> usedMemory;

    public AvailableMemory(Map<Integer, Long> freeMemory, Map<Integer, Long> usedMemory) {
        super(ResourceType.MEMORY);
        this.freeMemory = freeMemory;
        this.usedMemory = usedMemory;
    }

    @Override
    public <T> T accept(AvailableResourceVisitor<T> visitor) {
        return visitor.visit(this);
    }
}
