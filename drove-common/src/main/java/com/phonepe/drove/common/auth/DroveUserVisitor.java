package com.phonepe.drove.common.auth;

/**
 *
 */
public interface DroveUserVisitor<T> {
    T visit(final DroveClusterNode clusterNode);

    T visit(final DroveExternalUser externalUser);
}
