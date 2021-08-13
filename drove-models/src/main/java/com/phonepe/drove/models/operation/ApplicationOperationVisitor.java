package com.phonepe.drove.models.operation;

import com.phonepe.drove.models.operation.ops.*;

/**
 * Application related operations
 */
public interface ApplicationOperationVisitor<T> {

    T visit(ApplicationCreateOperation create);

    T visit(ApplicationUpdateOperation update);

    T visit(ApplicationInfoOperation info);

    T visit(ApplicationDestroyOperation destroy);

    T visit(ApplicationDeployOperation deploy);

    T visit(ApplicationScaleOperation scale);

    T visit(ApplicationRestartOperation restart);

    T visit(ApplicationSuspendOperation suspend);

}
