package com.phonepe.drove.common.auth.model;

/**
 *
 */
public interface DroveUserVisitor<T> {
    T visit(final DroveClusterNode clusterNode);

    T visit(final DroveExternalUser externalUser);
}
