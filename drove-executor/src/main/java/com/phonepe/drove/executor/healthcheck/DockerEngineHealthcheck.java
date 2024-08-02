/*
 *  Copyright (c) 2022 Original Author(s), PhonePe India Pvt. Ltd.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

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
