package com.phonepe.drove.executor.dockerauth;

import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.Value;

import javax.validation.constraints.NotEmpty;

/**
 *
 */
@Value
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class CredentialsDockerAuthConfigEntry extends DockerAuthConfigEntry {
    @NotEmpty
    String username;
    @NotEmpty
    String password;

    public CredentialsDockerAuthConfigEntry(String username, String password) {
        super(DockerAuthType.CREDENTIALS);
        this.username = username;
        this.password = password;
    }

    @Override
    public <T> T accept(DockerAuthConfigVisitor<T> visitor) {
        return visitor.visit(this);
    }
}
