package com.phonepe.drove.auth.core;

import lombok.Value;

/**
 *
 */
@Value
public class ClusterCredentials {
    String nodeId;
    String secret;
}
