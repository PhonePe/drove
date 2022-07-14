package com.phonepe.drove.auth.model;

/**
 *
 */
public interface DroveUserVisitor<T> {
    T visit(final DroveClusterNode clusterNode);

    T visit(final DroveApplicationInstance applicationInstance);

    T visit(final DroveExternalUser externalUser);

}
