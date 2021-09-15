package com.phonepe.drove.models.application.requirements;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import lombok.Data;

/**
 *
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXISTING_PROPERTY, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(name = "CPU", value = CPURequirement.class),
        @JsonSubTypes.Type(name = "MEMORY", value = MemoryRequirement.class),
})
@Data
public abstract class ResourceRequirement {
    private final ResourceType type;

    public abstract <T> T accept(final ResourceRequirementVisitor<T> visitor);
}
