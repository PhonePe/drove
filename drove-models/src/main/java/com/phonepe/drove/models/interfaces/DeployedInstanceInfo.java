package com.phonepe.drove.models.interfaces;

/**
 *
 */
public interface DeployedInstanceInfo {
    <T> T accept(final DeployedInstanceInfoVisitor<T> visitor);
}
