package com.phonepe.drove.executor.dockerauth;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import lombok.Data;

/**
 *
 */
@Data
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXISTING_PROPERTY, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(name = "CREDENTIALS", value = CredentialsDockerAuthConfigEntry.class)
})
public abstract class DockerAuthConfigEntry {
    private final DockerAuthType type;

    public abstract <T> T accept(final DockerAuthConfigVisitor<T> visitor);
}
