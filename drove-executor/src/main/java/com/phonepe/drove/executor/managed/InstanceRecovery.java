package com.phonepe.drove.executor.managed;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.dockerjava.api.DockerClient;
import com.phonepe.drove.common.model.ApplicationInstanceSpec;
import com.phonepe.drove.common.model.TaskInstanceSpec;
import com.phonepe.drove.executor.engine.ApplicationInstanceEngine;
import com.phonepe.drove.executor.engine.DockerLabels;
import com.phonepe.drove.executor.engine.TaskInstanceEngine;
import com.phonepe.drove.executor.model.ExecutorInstanceInfo;
import com.phonepe.drove.executor.model.ExecutorTaskInstanceInfo;
import com.phonepe.drove.models.application.JobType;
import com.phonepe.drove.models.instance.InstanceState;
import com.phonepe.drove.models.taskinstance.TaskInstanceState;
import com.phonepe.drove.statemachine.StateData;
import io.dropwizard.lifecycle.Managed;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import ru.vyarus.dropwizard.guice.module.installer.order.Order;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 *
 */
@Singleton
@Slf4j
@Order(70)
public class InstanceRecovery implements Managed {
    private final ApplicationInstanceEngine applicationInstanceEngine;
    private final TaskInstanceEngine taskInstanceEngine;
    private final ObjectMapper mapper;
    private final DockerClient client;

    @Inject
    public InstanceRecovery(
            ApplicationInstanceEngine applicationInstanceEngine,
            TaskInstanceEngine taskInstanceEngine,
            ObjectMapper mapper,
            DockerClient client) {
        this.applicationInstanceEngine = applicationInstanceEngine;
        this.taskInstanceEngine = taskInstanceEngine;
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
        val runningInstances = containers.stream()
                .collect(Collectors.groupingBy(container -> JobType.valueOf(Objects.requireNonNullElse(container.getLabels()
                                                                                                               .get(DockerLabels.DROVE_JOB_TYPE_LABEL),
                                                                                                       JobType.SERVICE.name()))));
        runningInstances
                .getOrDefault(JobType.SERVICE, List.of())
                .forEach(container -> {
            try {
                val id = container.getLabels().get(DockerLabels.DROVE_INSTANCE_ID_LABEL);
                val spec = mapper.readValue(container.getLabels()
                                                    .get(DockerLabels.DROVE_INSTANCE_SPEC_LABEL),
                                            ApplicationInstanceSpec.class);
                val data = mapper.readValue(container.getLabels()
                                                    .get(DockerLabels.DROVE_INSTANCE_DATA_LABEL),
                                            ExecutorInstanceInfo.class);
                val status = applicationInstanceEngine.registerInstance(id,
                                                                        spec,
                                                                        StateData.create(InstanceState.UNKNOWN, data));
                log.info("Recovery status for application instance {}: {}", id, status);
            }
            catch (JsonProcessingException e) {
                log.error("Error recovering state for container: " + container.getId(), e);
            }
        });
        runningInstances
                .getOrDefault(JobType.COMPUTATION, List.of())
                .forEach(container -> {
                    try {
                        val id = container.getLabels().get(DockerLabels.DROVE_INSTANCE_ID_LABEL);
                        val spec = mapper.readValue(container.getLabels()
                                                            .get(DockerLabels.DROVE_INSTANCE_SPEC_LABEL),
                                                    TaskInstanceSpec.class);
                        val data = mapper.readValue(container.getLabels()
                                                            .get(DockerLabels.DROVE_INSTANCE_DATA_LABEL),
                                                    ExecutorTaskInstanceInfo.class);
                        val status = taskInstanceEngine.registerInstance(id,
                                                                                spec,
                                                                                StateData.create(TaskInstanceState.UNKNOWN, data));
                        log.info("Recovery status for task instance {}: {}", id, status);
                    }
                    catch (JsonProcessingException e) {
                        log.error("Error recovering state for container: " + container.getId(), e);
                    }
                });
    }

}
