package com.phonepe.drove.executor.model;

/**
 *
 */
public interface DeployedExecutorInstanceInfoVisitor<T> {
    T visit(final ExecutorApplicationInstanceInfo applicationInstanceInfo);
    T visit(final ExecutorTaskInstanceInfo taskInstanceInfo);
}
