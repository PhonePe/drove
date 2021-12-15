package com.phonepe.drove.executor.healthcheck;

import com.github.dockerjava.api.DockerClient;
import com.google.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import ru.vyarus.dropwizard.guice.module.installer.feature.health.NamedHealthCheck;

/**
 *
 */
@Slf4j
public class DockerEngineHealthcheck extends NamedHealthCheck {
    private final DockerClient client;

    @Inject
    public DockerEngineHealthcheck(DockerClient client) {
        this.client = client;
    }

    @Override
    public String getName() {
        return "docker-engine";
    }

    @Override
    protected Result check() throws Exception {
        try {
            client.pingCmd().exec();
            return Result.healthy();
        } catch (Exception e) {
            log.error("Error running ping check: " + e.getMessage(), e);
            return Result.unhealthy(e.getMessage());
        }
    }
}
