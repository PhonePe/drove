package com.phonepe.drove.executor.model;

/**
 *
 */
public interface DeployedExecutorInstanceInfo {
    <T> T accept(final DeployedExecutorInstanceInfoVisitor<T> visitor);
}
