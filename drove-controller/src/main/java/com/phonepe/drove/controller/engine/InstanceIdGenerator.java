package com.phonepe.drove.controller.engine;

import com.phonepe.drove.models.interfaces.DeploymentSpec;

/**
 *
 */
public interface InstanceIdGenerator {
    String generate(DeploymentSpec spec);
}
