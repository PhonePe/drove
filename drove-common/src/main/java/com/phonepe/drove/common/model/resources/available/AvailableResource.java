package com.phonepe.drove.common.model.resources.available;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.phonepe.drove.models.application.requirements.ResourceType;
import lombok.Data;

/**
 *
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXISTING_PROPERTY, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(name = "CPU", value = AvailableCPU.class),
        @JsonSubTypes.Type(name = "MEMORY", value = AvailableMemory.class),
})
@Data
public abstract class AvailableResource {
    private final ResourceType type;

    public abstract <T> T accept(final AvailableResourceVisitor<T> visitor);
}
