package com.phonepe.drove.executor.statemachine.actions;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.dockerjava.api.model.ExposedPort;
import com.github.dockerjava.api.model.HostConfig;
import com.github.dockerjava.api.model.Ports;
import com.phonepe.drove.common.CommonUtils;
import com.phonepe.drove.common.StateData;
import com.phonepe.drove.common.model.InstanceSpec;
import com.phonepe.drove.common.model.resources.allocation.CPUAllocation;
import com.phonepe.drove.common.model.resources.allocation.MemoryAllocation;
import com.phonepe.drove.common.model.resources.allocation.ResourceAllocationVisitor;
import com.phonepe.drove.executor.engine.DockerLabels;
import com.phonepe.drove.executor.engine.InstanceLogHandler;
import com.phonepe.drove.executor.statemachine.InstanceAction;
import com.phonepe.drove.executor.statemachine.InstanceActionContext;
import com.phonepe.drove.models.application.executable.DockerCoordinates;
import com.phonepe.drove.models.instance.InstanceInfo;
import com.phonepe.drove.models.instance.InstancePort;
import com.phonepe.drove.models.instance.InstanceState;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.MDC;

import java.net.ServerSocket;
import java.util.*;
import java.util.stream.Collectors;

/**
 *
 */
@Slf4j
public class InstanceRunAction extends InstanceAction {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    protected StateData<InstanceState, InstanceInfo> executeImpl(
            InstanceActionContext context, StateData<InstanceState, InstanceInfo> currentState) {
        val instanceSpec = context.getInstanceSpec();
        val client = context.getClient();
        val image = instanceSpec.getExecutable().accept(DockerCoordinates::getUrl);

        try (val containerCmd = client.createContainerCmd(UUID.randomUUID().toString())) {
            containerCmd
                    .withImage(image)
                    .withName(instanceSpec.getAppId() + UUID.randomUUID());
            val hostConfig = new HostConfig()
                    .withMemorySwappiness(0L)
                    .withOomKillDisable(true)
                    .withAutoRemove(true)/*
                    .withPublishAllPorts(true)*/;

            instanceSpec.getResources()
                    .forEach(resourceRequirement -> resourceRequirement.accept(new ResourceAllocationVisitor<Void>() {
                        @Override
                        public Void visit(CPUAllocation cpu) {
                            hostConfig.withCpuCount((long) cpu.getCores().size());
                            hostConfig.withCpusetCpus(StringUtils.join(cpu.getCores().values().stream().flatMap(Set::stream).map(i -> Integer.toString(i)).collect(
                                    Collectors.toUnmodifiableList()), ","));
                            return null;
                        }

                        @Override
                        public Void visit(MemoryAllocation memory) {
                            hostConfig.withMemory(memory.getMemoryInMB()
                                                          .values()
                                                          .stream()
                                                          .mapToLong(Long::longValue)
                                                          .sum() * (1 << 20));
                            hostConfig.withCpusetMems(StringUtils.join(memory.getMemoryInMB().keySet(), ","));
                            return null;
                        }
                    }));
            val ports = new Ports();
            val env = new ArrayList<String>();
            val portMappings = new HashMap<String, InstancePort>();
            val hostName = CommonUtils.hostname();
            env.add("HOST=" + hostName);
            instanceSpec.getPorts().forEach(
                    portSpec -> {
                        val freePort = findFreePort();
                        val specPort = portSpec.getPort();
                        ports.bind(new ExposedPort(specPort), Ports.Binding.bindPort(freePort));
                        env.add(String.format("PORT_%d=%d", specPort, freePort));
                        portMappings.put(portSpec.getName(), new InstancePort(portSpec.getPort(), freePort));
                    });
            hostConfig.withPortBindings(ports);

            val instanceInfo = instanceInfo(context, instanceSpec, portMappings, hostName);
            val labels = new HashMap<String, String>();
            labels.put(DockerLabels.DROVE_INSTANCE_ID_LABEL, instanceSpec.getInstanceId());
            labels.put(DockerLabels.DROVE_INSTANCE_SPEC_LABEL, MAPPER.writeValueAsString(instanceSpec));
            labels.put(DockerLabels.DROVE_INSTANCE_DATA_LABEL, MAPPER.writeValueAsString(instanceInfo));

            val id = containerCmd
                    .withHostConfig(hostConfig)
                    .withEnv(env)
                    .withLabels(labels)
                    .exec()
                    .getId();
            log.debug("Created container id: {}", id);

            context.setDockerInstanceId(id);
            client.startContainerCmd(id)
                    .exec();
            client.logContainerCmd(id)
                    .withTailAll()
                    .withFollowStream(true)
                    .withStdOut(true)
                    .withStdErr(true)
                    .exec(new InstanceLogHandler(MDC.getCopyOfContextMap()));
            return StateData.create(InstanceState.UNREADY,
                                    instanceInfo);
        }
        catch (Exception e) {
            log.error("Error creating container: ", e);
            return StateData.errorFrom(currentState, InstanceState.START_FAILED, e.getMessage());
        }
    }

    private InstanceInfo instanceInfo(
            InstanceActionContext context, InstanceSpec instanceSpec,
            HashMap<String, InstancePort> portMappings,
            String hostName) {
        return new InstanceInfo(instanceSpec.getAppId(),
                                instanceSpec.getInstanceId(),
                                context.getExecutorId(),
                                hostName,
                                InstanceState.UNREADY,
                                portMappings,
                                Collections.emptyMap(),
                                new Date(),
                                new Date());
    }

    @Override
    public void stop() {

    }

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
}
