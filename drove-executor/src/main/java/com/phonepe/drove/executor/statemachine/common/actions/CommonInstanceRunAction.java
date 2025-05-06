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

package com.phonepe.drove.executor.statemachine.common.actions;

import com.codahale.metrics.MetricRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.dockerjava.api.model.ExposedPort;
import com.github.dockerjava.api.model.Ports;
import com.phonepe.drove.common.model.*;
import com.phonepe.drove.common.net.HttpCaller;
import com.phonepe.drove.executor.ExecutorOptions;
import com.phonepe.drove.executor.engine.DockerLabels;
import com.phonepe.drove.executor.engine.InstanceLogHandler;
import com.phonepe.drove.executor.model.DeployedExecutionObjectInfo;
import com.phonepe.drove.executor.resourcemgmt.ResourceConfig;
import com.phonepe.drove.executor.resourcemgmt.ResourceManager;
import com.phonepe.drove.executor.statemachine.ExecutorActionBase;
import com.phonepe.drove.executor.statemachine.InstanceActionContext;
import com.phonepe.drove.executor.utils.DockerUtils;
import com.phonepe.drove.models.application.PortSpec;
import com.phonepe.drove.models.info.resources.allocation.ResourceAllocation;
import com.phonepe.drove.models.instance.InstancePort;
import com.phonepe.drove.statemachine.StateData;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.slf4j.MDC;

import java.net.ServerSocket;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static com.phonepe.drove.common.CommonUtils.hostname;

/**
 *
 */
@Slf4j
public abstract class CommonInstanceRunAction<E extends DeployedExecutionObjectInfo, S extends Enum<S>,
        T extends DeploymentUnitSpec> extends ExecutorActionBase<E, S, T> {
    private final ResourceConfig schedulingConfig;
    private final ExecutorOptions executorOptions;
    private final HttpCaller httpCaller;
    private final ObjectMapper mapper;
    private final MetricRegistry metricRegistry;
    private final ResourceManager resourceManager;

    protected CommonInstanceRunAction(
            ResourceConfig schedulingConfig,
            ExecutorOptions executorOptions,
            HttpCaller httpCaller,
            ObjectMapper mapper,
            MetricRegistry metricRegistry,
            ResourceManager resourceManager) {
        this.schedulingConfig = schedulingConfig;
        this.executorOptions = executorOptions;
        this.httpCaller = httpCaller;
        this.mapper = mapper;
        this.metricRegistry = metricRegistry;
        this.resourceManager = resourceManager;
    }


    @Override
    protected StateData<S, E> executeImpl(InstanceActionContext<T> context, StateData<S, E> currentState) {
        val instanceSpec = context.getInstanceSpec();
        val client = context.getClient();
        try {
            val instanceInfoRef = new AtomicReference<E>();
            val containerId = DockerUtils.createContainer(
                    schedulingConfig,
                    client,
                    containerId(instanceSpec),
                    instanceSpec,
                    params -> {
                        val ports = new Ports();
                        val exposedPorts = params.getExposedPorts();
                        val portMappings = new HashMap<String, InstancePort>();
                        portSpecs(instanceSpec)
                                .forEach(
                                portSpec -> {
                                    val containerPort = params.isHostLevelInstance()
                                                        ? portSpec.getPort()
                                                        : findFreePort();
                                    val specPort = portSpec.getPort();
                                    val exposedPort = new ExposedPort(specPort);
                                    ports.bind(exposedPort, Ports.Binding.bindPort(containerPort));
                                    exposedPorts.add(exposedPort);
                                    params.getCustomEnv().add(String.format("PORT_%d=%d", specPort, containerPort));
                                    portMappings.put(portSpec.getName(),
                                                     new InstancePort(portSpec.getPort(),
                                                                      containerPort,
                                                                      portSpec.getType()));
                                });
                        params.getHostConfig().withPortBindings(ports);
                        val instanceInfo = instanceInfo(currentState,
                                                        portMappings,
                                                        instanceSpec.getResources(),
                                                        params.getHostname(),
                                                        currentState.getData());
                        instanceInfoRef.set(instanceInfo);
                        val labels = params.getCustomLabels();
                        labels.putAll(instanceSpecificLabels(instanceSpec));
                        labels.put(DockerLabels.DROVE_INSTANCE_SPEC_LABEL, mapper.writeValueAsString(instanceSpec));
                        labels.put(DockerLabels.DROVE_INSTANCE_DATA_LABEL, mapper.writeValueAsString(instanceInfo));

                        val env = params.getCustomEnv();
                        env.add("DROVE_EXECUTOR_HOST=" + hostname());
                        env.addAll(instanceSpecificEnv(instanceSpec));
                    },
                    executorOptions,
                    resourceManager);
            DockerUtils.injectConfigs(containerId, context.getClient(), instanceSpec, httpCaller);
            context.setDockerInstanceId(containerId);
            client.startContainerCmd(containerId)
                    .exec();
            client.logContainerCmd(containerId)
                    .withTailAll()
                    .withFollowStream(true)
                    .withStdOut(true)
                    .withStdErr(true)
                    .exec(new InstanceLogHandler(MDC.getCopyOfContextMap(),
                                                 instanceInfoRef.get(),
                                                 metricRegistry
                    ));
            return successState(currentState, instanceInfoRef.get());
        }
        catch (Exception e) {
            log.error("Error creating container: ", e);
            return errorState(currentState, e);
        }
    }

    protected abstract StateData<S, E> successState(StateData<S, E> currentState, E instanceInfo);
    protected abstract StateData<S, E> errorState(StateData<S, E> currentState, Throwable e);
    protected abstract Map<String, String> instanceSpecificLabels(final T spec);
    protected abstract List<String> instanceSpecificEnv(final T spec);

    protected abstract E instanceInfo(
            StateData<S, E> currentState,
            HashMap<String, InstancePort> portMappings,
            List<ResourceAllocation> resources,
            String hostname,
            E data);

    private int findFreePort() {
        /*//IANA recommended range
        IntStream.rangeClosed(49152, 65535)
                .filter(port -> try(val s ))*/
        try (val s = new ServerSocket(0)) {
            return s.getLocalPort();
        }
        catch (Exception e) {
            log.error("Port allocation failure");
        }
        return 0;
    }

    private String containerId(final DeploymentUnitSpec spec) {
        return spec.accept(new DeploymentUnitSpecVisitor<String>() {
            @Override
            public String visit(ApplicationInstanceSpec instanceSpec) {
                return instanceSpec.getAppId() + "-" + instanceSpec.getInstanceId();
            }

            @Override
            public String visit(TaskInstanceSpec taskInstanceSpec) {
                return taskInstanceSpec.getSourceAppName() + "-" + taskInstanceSpec.getInstanceId();
            }

            @Override
            public String visit(LocalServiceInstanceSpec localServiceInstanceSpec) {
                return localServiceInstanceSpec.getServiceId() + "-" + localServiceInstanceSpec.getInstanceId();
            }
        });
    }

    private Collection<PortSpec> portSpecs(final DeploymentUnitSpec spec) {
        return spec.accept(new DeploymentUnitSpecVisitor<Collection<PortSpec>>() {
            @Override
            public Collection<PortSpec> visit(ApplicationInstanceSpec instanceSpec) {
                return instanceSpec.getPorts();
            }

            @Override
            public Collection<PortSpec> visit(TaskInstanceSpec taskInstanceSpec) {
                return List.of();
            }

            @Override
            public Collection<PortSpec> visit(LocalServiceInstanceSpec localServiceInstanceSpec) {
                return localServiceInstanceSpec.getPorts();
            }
        });
    }

    @Override
    public void stop() {
        //Nothing to do here
    }
}
