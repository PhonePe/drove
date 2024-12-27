/*
 *  Copyright (c) 2024 Original Author(s), PhonePe India Pvt. Ltd.
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

package com.phonepe.drove.executor.managed;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.model.Container;
import com.phonepe.drove.common.model.ApplicationInstanceSpec;
import com.phonepe.drove.common.model.LocalServiceInstanceSpec;
import com.phonepe.drove.common.model.TaskInstanceSpec;
import com.phonepe.drove.executor.discovery.ClusterClient;
import com.phonepe.drove.executor.engine.ApplicationInstanceEngine;
import com.phonepe.drove.executor.engine.DockerLabels;
import com.phonepe.drove.executor.engine.LocalServiceInstanceEngine;
import com.phonepe.drove.executor.engine.TaskInstanceEngine;
import com.phonepe.drove.executor.model.ExecutorInstanceInfo;
import com.phonepe.drove.executor.model.ExecutorLocalServiceInstanceInfo;
import com.phonepe.drove.executor.model.ExecutorTaskInfo;
import com.phonepe.drove.models.application.JobType;
import com.phonepe.drove.models.instance.InstanceState;
import com.phonepe.drove.models.instance.LocalServiceInstanceState;
import com.phonepe.drove.models.taskinstance.TaskState;
import com.phonepe.drove.statemachine.StateData;
import io.dropwizard.lifecycle.Managed;
import io.dropwizard.lifecycle.ServerLifecycleListener;
import io.dropwizard.setup.Environment;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.eclipse.jetty.server.Server;
import ru.vyarus.dropwizard.guice.module.installer.order.Order;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 *
 */
@Singleton
@Slf4j
@Order(70)
public class InstanceRecovery implements Managed, ServerLifecycleListener {
    private final ApplicationInstanceEngine applicationInstanceEngine;
    private final TaskInstanceEngine taskInstanceEngine;
    private final LocalServiceInstanceEngine localServiceInstanceEngine;
    private final ObjectMapper mapper;
    private final DockerClient client;
    private final ClusterClient clusterClient;

    @Inject
    public InstanceRecovery(
            ApplicationInstanceEngine applicationInstanceEngine,
            TaskInstanceEngine taskInstanceEngine,
            LocalServiceInstanceEngine localServiceInstanceEngine,
            ObjectMapper mapper,
            DockerClient client,
            ClusterClient clusterClient,
            Environment environment) {
        this.applicationInstanceEngine = applicationInstanceEngine;
        this.taskInstanceEngine = taskInstanceEngine;
        this.mapper = mapper;
        this.client = client;
        this.clusterClient = clusterClient;
        this.localServiceInstanceEngine = localServiceInstanceEngine;
        environment.lifecycle().addServerLifecycleListener(this);
    }


    @Override
    public void start() throws Exception {
        log.info("Managed instance recovery system started");
    }

    @Override
    public void stop() throws Exception {
        log.info("Managed instance recovery system stopped");
    }

    @Override
    public void serverStarted(Server server) {
        log.info("State recovery started");
        recoverState();
        log.info("State recovery completed");
    }

    private void recoverState() {
        val containers = client.listContainersCmd()
                .withLabelFilter(List.of(DockerLabels.DROVE_INSTANCE_ID_LABEL,
                                         DockerLabels.DROVE_INSTANCE_SPEC_LABEL,
                                         DockerLabels.DROVE_INSTANCE_DATA_LABEL))
                .exec();
        if (containers.isEmpty()) {
            log.info("No running containers found. Recovery is not necessary");
            return;
        }
        val knownInstances = clusterClient.lastKnownInstances();
        log.debug("Known instances data: {}", knownInstances);
        val runningInstances = containers.stream()
                .collect(Collectors.groupingBy(container -> JobType.valueOf(Objects.requireNonNullElse(container.getLabels()
                                                                                                               .get(DockerLabels.DROVE_JOB_TYPE_LABEL),
                                                                                                       JobType.SERVICE.name()))));
        val knownAppInstances = knownInstances.getAppInstanceIds();
        val staleAppInstances = knownInstances.getStaleAppInstanceIds();
        val knownTaskInstances = knownInstances.getTaskInstanceIds();
        val staleTaskInstances = knownInstances.getStaleTaskInstanceIds();
        val knownLocalServiceInstances = knownInstances.getLocalServiceInstanceIds();
        val staleLocalServiceInstances = knownInstances.getStaleLocalServiceInstanceIds();

        recoverAppInstances(runningInstances, knownAppInstances, staleAppInstances);
        recoverTaskInstances(runningInstances, knownTaskInstances, staleTaskInstances);
        recoverLocalServiceInstances(runningInstances, knownLocalServiceInstances, staleLocalServiceInstances);
    }

    private void recoverAppInstances(
            Map<JobType, List<Container>> runningInstances,
            Set<String> knownAppInstances,
            Set<String> staleAppInstances) {
        runningInstances
                .getOrDefault(JobType.SERVICE, List.of())
                .forEach(container -> {
                    val id = container.getLabels().get(DockerLabels.DROVE_INSTANCE_ID_LABEL);
                    if ((knownAppInstances.isEmpty() || knownAppInstances.contains(id))
                            && !staleAppInstances.contains(id)) {
                        try {
                            val spec = mapper.readValue(container.getLabels()
                                                                .get(DockerLabels.DROVE_INSTANCE_SPEC_LABEL),
                                                        ApplicationInstanceSpec.class);
                            val data = mapper.readValue(container.getLabels()
                                                                .get(DockerLabels.DROVE_INSTANCE_DATA_LABEL),
                                                        ExecutorInstanceInfo.class);
                            val status = applicationInstanceEngine.registerInstance(
                                    id, spec, StateData.create(InstanceState.UNKNOWN, data));
                            log.info("Recovery status for application instance {}: {}", id, status);
                        }
                        catch (JsonProcessingException e) {
                            log.error("Error recovering state for app instance container: " + container.getId(), e);
                        }
                    }
                    else {
                        log.warn(
                                "Unknown application instance {} found to be running. Ignoring it. This will get reaped by the zombie reaper later on",
                                id);
                    }
                });
    }

    private void recoverTaskInstances(
            Map<JobType, List<Container>> runningInstances, Set<String> knownTaskInstances,
            Set<String> staleTaskInstances) {
        runningInstances
                .getOrDefault(JobType.COMPUTATION, List.of())
                .forEach(container -> {
                    val id = container.getLabels().get(DockerLabels.DROVE_INSTANCE_ID_LABEL);
                    if ((knownTaskInstances.isEmpty() || knownTaskInstances.contains(id))
                            && !staleTaskInstances.contains(id)) {
                        try {
                            val spec = mapper.readValue(container.getLabels()
                                                                .get(DockerLabels.DROVE_INSTANCE_SPEC_LABEL),
                                                        TaskInstanceSpec.class);
                            val data = mapper.readValue(container.getLabels()
                                                                .get(DockerLabels.DROVE_INSTANCE_DATA_LABEL),
                                                        ExecutorTaskInfo.class);
                            val status = taskInstanceEngine.registerInstance(
                                    id, spec, StateData.create(TaskState.UNKNOWN, data));
                            log.info("Recovery status for task instance {}: {}", id, status);
                        }
                        catch (JsonProcessingException e) {
                            log.error("Error recovering state for container: " + container.getId(), e);
                        }
                    }
                    else {
                        log.warn(
                                "Unknown task instance {} found to be running. Ignoring it. This will get reaped by the zombie reaper later on",
                                id);
                    }
                });
    }

    private void recoverLocalServiceInstances(
            Map<JobType, List<Container>> runningInstances,
            Set<String> knownAppInstances,
            Set<String> staleAppInstances) {
        runningInstances
                .getOrDefault(JobType.LOCAL_SERVICE, List.of())
                .forEach(container -> {
                    val id = container.getLabels().get(DockerLabels.DROVE_INSTANCE_ID_LABEL);
                    if ((knownAppInstances.isEmpty() || knownAppInstances.contains(id))
                            && !staleAppInstances.contains(id)) {
                        try {
                            val spec = mapper.readValue(container.getLabels()
                                                                .get(DockerLabels.DROVE_INSTANCE_SPEC_LABEL),
                                                        LocalServiceInstanceSpec.class);
                            val data = mapper.readValue(container.getLabels()
                                                                .get(DockerLabels.DROVE_INSTANCE_DATA_LABEL),
                                                        ExecutorLocalServiceInstanceInfo.class);
                            val status = localServiceInstanceEngine.registerInstance(
                                    id, spec, StateData.create(LocalServiceInstanceState.UNKNOWN, data));
                            log.info("Recovery status for local service instance {}: {}", id, status);
                        }
                        catch (JsonProcessingException e) {
                            log.error("Error recovering state for container: " + container.getId(), e);
                        }
                    }
                    else {
                        log.warn(
                                "Unknown local service instance {} found to be running. Ignoring it. This will get reaped by the zombie reaper later on",
                                id);
                    }
                });
    }
}
