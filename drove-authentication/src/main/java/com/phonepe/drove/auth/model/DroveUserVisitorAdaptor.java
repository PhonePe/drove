package com.phonepe.drove.auth.model;

/**
 *
 */
public abstract class DroveUserVisitorAdaptor<T> implements DroveUserVisitor<T> {
    private final T defaultValue;

    protected DroveUserVisitorAdaptor(T defaultValue) {
        this.defaultValue = defaultValue;
    }

    @Override
    public T visit(DroveClusterNode clusterNode) {
        return defaultValue;
    }

    @Override
    public T visit(DroveApplicationInstance applicationInstance) {
        return defaultValue;
    }

    @Override
    public T visit(DroveExternalUser externalUser) {
        return defaultValue;
    }
}
