package com.phonepe.drove.common.model.resources.available;

import com.fasterxml.jackson.annotation.JsonProperty;
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
    Map<Integer, Long> freeMemory;
    Map<Integer, Long> usedMemory;

    public AvailableMemory(
            @JsonProperty("freeMemory") Map<Integer, Long> freeMemory,
            @JsonProperty("usedMemory") Map<Integer, Long> usedMemory) {
        super(ResourceType.MEMORY);
        this.freeMemory = freeMemory;
        this.usedMemory = usedMemory;
    }

    @Override
    public <T> T accept(AvailableResourceVisitor<T> visitor) {
        return visitor.visit(this);
    }
}
