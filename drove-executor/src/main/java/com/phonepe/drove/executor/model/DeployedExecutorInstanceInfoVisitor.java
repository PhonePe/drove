package com.phonepe.drove.executor.model;

/**
 *
 */
public interface DeployedExecutorInstanceInfoVisitor<T> {
    T visit(final ExecutorInstanceInfo applicationInstanceInfo);
    T visit(final ExecutorTaskInfo taskInfo);
}
