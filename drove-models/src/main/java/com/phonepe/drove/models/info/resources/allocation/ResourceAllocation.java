package com.phonepe.drove.models.info.resources.allocation;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.phonepe.drove.models.application.requirements.ResourceType;
import lombok.Data;

/**
 *
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXISTING_PROPERTY, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(name = "CPU", value = CPUAllocation.class),
        @JsonSubTypes.Type(name = "MEMORY", value = MemoryAllocation.class),
})
@Data
public abstract class ResourceAllocation {
    private final ResourceType type;

    public abstract <T> T accept(final ResourceAllocationVisitor<T> visitor);
}
