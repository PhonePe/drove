package com.phonepe.drove.executor.model;

/**
 *
 */
public interface DeployedExecutionObjectInfo {
    <T> T accept(final DeployedExecutorInstanceInfoVisitor<T> visitor);
}
