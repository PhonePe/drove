package com.phonepe.drove.models.operation;

import com.phonepe.drove.models.operation.ops.*;

/**
 * Application related operations
 */
public interface ApplicationOperationVisitor<T> {

    T visit(ApplicationCreateOperation create);

    T visit(ApplicationDestroyOperation destroy);

    T visit(ApplicationStartInstancesOperation deploy);

    T visit(ApplicationStopInstancesOperation stopInstances);

    T visit(ApplicationScaleOperation scale);

    T visit(ApplicationReplaceInstancesOperation replaceInstances);

    T visit(ApplicationSuspendOperation suspend);

    T visit(ApplicationRecoverOperation recover);
}
