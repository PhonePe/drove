package com.phonepe.drove.executor.healthcheck;

import ru.vyarus.dropwizard.guice.module.installer.feature.health.NamedHealthCheck;

/**
 *
 */
public class DockerEngineHealthcheck extends NamedHealthCheck {
    @Override
    public String getName() {
        return "docker-engine";
    }

    @Override
    protected Result check() throws Exception {
        return Result.healthy();
    }
}
