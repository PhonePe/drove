package com.phonepe.drove.models.application.executable;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import lombok.Data;

/**
 * Coordinates to find the executable to be deployed across the cluster
 */
@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.EXISTING_PROPERTY,
        property = "type",
        defaultImpl = DockerCoordinates.class)
@JsonSubTypes({
        @JsonSubTypes.Type(name = "DOCKER", value = DockerCoordinates.class)
})
@Data
public abstract class ExecutableCoordinates {
    private final ExecutableType type;

    public abstract <T> T accept(final ExecutableTypeVisitor<T> visitor);
}
