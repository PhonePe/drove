package com.phonepe.drove.executor.dockerauth;

/**
 *
 */
public interface DockerAuthConfigVisitor<T> {
    T visit(CredentialsDockerAuthConfigEntry credentials);
}
