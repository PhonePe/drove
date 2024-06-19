package com.phonepe.drove.auth.model;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 *
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class ClusterCommHeaders {
    public static final String CLUSTER_AUTHORIZATION = "Cluster-Authorization";
    public static final String APP_INSTANCE_AUTHORIZATION = "App-Instance-Authorization";
}
