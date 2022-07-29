package com.phonepe.drove.controller.engine;

import com.phonepe.drove.models.application.ApplicationSpec;
import com.phonepe.drove.models.interfaces.DeploymentSpec;
import com.phonepe.drove.models.interfaces.DeploymentSpecVisitor;
import com.phonepe.drove.models.task.TaskSpec;

import javax.inject.Singleton;
import java.util.UUID;

/**
 *
 */
@Singleton
public class RandomInstanceIdGenerator implements InstanceIdGenerator {
    @Override
    public String generate(DeploymentSpec spec) {
        return spec.accept(new DeploymentSpecVisitor<String>() {
            @Override
            public String visit(ApplicationSpec applicationSpec) {
                return "AI-";
            }

            @Override
            public String visit(TaskSpec taskSpec) {
                return "TI-";
            }
        }) + UUID.randomUUID().toString();
    }
}
