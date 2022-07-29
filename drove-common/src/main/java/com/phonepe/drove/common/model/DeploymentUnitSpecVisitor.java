package com.phonepe.drove.common.model;

/**
 *
 */
public interface DeploymentUnitSpecVisitor<T> {
    T visit(final ApplicationInstanceSpec instanceSpec);

    T visit(TaskInstanceSpec taskInstanceSpec);
}
