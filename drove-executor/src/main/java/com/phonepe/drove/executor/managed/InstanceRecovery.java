package com.phonepe.drove.executor.managed;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientImpl;
import com.github.dockerjava.zerodep.ZerodepDockerHttpClient;
import com.google.common.base.Strings;
import com.phonepe.drove.common.StateData;
import com.phonepe.drove.executor.engine.DockerLabels;
import com.phonepe.drove.executor.engine.InstanceEngine;
import com.phonepe.drove.common.model.InstanceSpec;
import com.phonepe.drove.models.instance.InstanceInfo;
import com.phonepe.drove.models.instance.InstanceState;
import io.dropwizard.lifecycle.Managed;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import ru.vyarus.dropwizard.guice.module.installer.order.Order;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.net.URI;
import java.util.Collections;

/**
 *
 */
@Singleton
@Slf4j
@Order(10)
public class InstanceRecovery implements Managed {
    private final InstanceEngine engine;
    private final ObjectMapper mapper;

    @Inject
    public InstanceRecovery(InstanceEngine engine, ObjectMapper mapper) {
        this.engine = engine;
        this.mapper = mapper;
    }


    @Override
    public void start() throws Exception {
        log.info("State recovery started");
        recoverState();
        log.info("State recovery completed");
    }

    @Override
    public void stop() throws Exception {

    }

    private void recoverState() {
        val client = DockerClientImpl.getInstance(DefaultDockerClientConfig.createDefaultConfigBuilder()
                                                          .build(),
                                                  new ZerodepDockerHttpClient.Builder()
                                                          .dockerHost(URI.create("unix:///var/run/docker.sock"))
                                                          .build());
        val containers = client.listContainersCmd()
                .withLabelFilter(Collections.singletonList(DockerLabels.DROVE_INSTANCE_ID_LABEL))
                .exec();
        containers.forEach(container -> {
            try {
                val id = container.getLabels().get(DockerLabels.DROVE_INSTANCE_ID_LABEL);
                if (Strings.isNullOrEmpty(id)) {
                    log.info("Container {} is not a drove container. Won't be recovered", container.getId());
                }
                val spec = mapper.readValue(container.getLabels()
                                                    .get(DockerLabels.DROVE_INSTANCE_SPEC_LABEL),
                                            InstanceSpec.class);
                val data = mapper.readValue(container.getLabels()
                                                    .get(DockerLabels.DROVE_INSTANCE_DATA_LABEL),
                                            InstanceInfo.class);
                engine.registerInstance(id,
                                        spec,
                                        StateData.create(InstanceState.UNKNOWN, data));
            }
            catch (JsonProcessingException e) {
                log.error("Error recovering state for container: " + container.getId(), e);
            }
        });
    }

}
