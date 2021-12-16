package com.phonepe.drove.executor.managed;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.dockerjava.api.DockerClient;
import com.phonepe.drove.common.StateData;
import com.phonepe.drove.common.model.InstanceSpec;
import com.phonepe.drove.executor.engine.DockerLabels;
import com.phonepe.drove.executor.engine.InstanceEngine;
import com.phonepe.drove.executor.model.ExecutorInstanceInfo;
import com.phonepe.drove.models.instance.InstanceState;
import io.dropwizard.lifecycle.Managed;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import ru.vyarus.dropwizard.guice.module.installer.order.Order;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.List;

/**
 *
 */
@Singleton
@Slf4j
@Order(70)
public class InstanceRecovery implements Managed {
    private final InstanceEngine engine;
    private final ObjectMapper mapper;
    private final DockerClient client;

    @Inject
    public InstanceRecovery(
            InstanceEngine engine,
            ObjectMapper mapper,
            DockerClient client) {
        this.engine = engine;
        this.mapper = mapper;
        this.client = client;
    }


    @Override
    public void start() throws Exception {
        log.info("State recovery started");
        recoverState();
        log.info("State recovery completed");
    }

    @Override
    public void stop() throws Exception {
        log.info("Instance recovery stopped");
    }

    private void recoverState() {
        val containers = client.listContainersCmd()
                .withLabelFilter(List.of(DockerLabels.DROVE_INSTANCE_ID_LABEL,
                                         DockerLabels.DROVE_INSTANCE_SPEC_LABEL,
                                         DockerLabels.DROVE_INSTANCE_DATA_LABEL))
                .exec();
        containers.forEach(container -> {
            try {
                val id = container.getLabels().get(DockerLabels.DROVE_INSTANCE_ID_LABEL);
                val spec = mapper.readValue(container.getLabels()
                                                    .get(DockerLabels.DROVE_INSTANCE_SPEC_LABEL),
                                            InstanceSpec.class);
                val data = mapper.readValue(container.getLabels()
                                                    .get(DockerLabels.DROVE_INSTANCE_DATA_LABEL),
                                            ExecutorInstanceInfo.class);
                val status = engine.registerInstance(id,
                                                     spec,
                                                     StateData.create(InstanceState.UNKNOWN, data));
                log.info("Recovery status for instance {}: {}", id, status);
            }
            catch (JsonProcessingException e) {
                log.error("Error recovering state for container: " + container.getId(), e);
            }
        });
    }

}
