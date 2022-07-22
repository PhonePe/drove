package com.phonepe.drove.models.interfaces;

import com.phonepe.drove.models.instance.InstanceInfo;
import com.phonepe.drove.models.taskinstance.TaskInstanceInfo;

/**
 *
 */
public interface DeployedInstanceInfoVisitor<T> {
    T visit(final InstanceInfo applicationInstanceInfo);

    T visit(final TaskInstanceInfo taskInstanceInfo);
}
