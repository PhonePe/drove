package com.phonepe.drove.common.auth;

import lombok.Value;

/**
 *
 */
@Value
public class ClusterCredentials {
    String nodeId;
    String secret;
}
