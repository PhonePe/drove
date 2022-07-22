package com.phonepe.drove.models.interfaces;

import com.phonepe.drove.models.application.ApplicationSpec;
import com.phonepe.drove.models.task.TaskSpec;

/**
 *
 */
public interface DeploymentSpecVisitor<T> {
    T visit(final ApplicationSpec applicationSpec);
    T visit(final TaskSpec taskSpec);
}
