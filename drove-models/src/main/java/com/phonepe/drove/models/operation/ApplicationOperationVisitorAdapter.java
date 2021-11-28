package com.phonepe.drove.models.operation;

import com.phonepe.drove.models.operation.ops.*;

/**
 *
 */
public class ApplicationOperationVisitorAdapter<T> implements ApplicationOperationVisitor<T> {
    private final T defaultValue;

    public ApplicationOperationVisitorAdapter(T defaultValue) {
        this.defaultValue = defaultValue;
    }

    @Override
    public T visit(ApplicationCreateOperation create) {
        return defaultValue;
    }

    @Override
    public T visit(ApplicationDestroyOperation destroy) {
        return defaultValue;
    }

    @Override
    public T visit(ApplicationDeployOperation deploy) {
        return defaultValue;
    }

    @Override
    public T visit(ApplicationStopInstancesOperation stopInstances) {
        return defaultValue;
    }

    @Override
    public T visit(ApplicationScaleOperation scale) {
        return defaultValue;
    }

    @Override
    public T visit(ApplicationReplaceInstancesOperation replaceInstances) {
        return defaultValue;
    }

    @Override
    public T visit(ApplicationSuspendOperation suspend) {
        return defaultValue;
    }

    @Override
    public T visit(ApplicationRecoverOperation recover) {
        return defaultValue;
    }
}
