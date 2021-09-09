package com.phonepe.drove.executor.statemachine.actions;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.model.ExposedPort;
import com.github.dockerjava.api.model.Frame;
import com.github.dockerjava.api.model.HostConfig;
import com.github.dockerjava.api.model.Ports;
import com.phonepe.drove.common.StateData;
import com.phonepe.drove.executor.engine.DockerLabels;
import com.phonepe.drove.executor.statemachine.InstanceAction;
import com.phonepe.drove.executor.statemachine.InstanceActionContext;
import com.phonepe.drove.internalmodels.InstanceSpec;
import com.phonepe.drove.models.application.executable.DockerCoordinates;
import com.phonepe.drove.models.application.requirements.CPURequirement;
import com.phonepe.drove.models.application.requirements.MemoryRequirement;
import com.phonepe.drove.models.application.requirements.ResourceRequirementVisitor;
import com.phonepe.drove.models.instance.InstanceInfo;
import com.phonepe.drove.models.instance.InstancePort;
import com.phonepe.drove.models.instance.InstanceState;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

import java.net.InetAddress;
import java.net.ServerSocket;
import java.nio.charset.Charset;
import java.util.*;

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
                    .withName(instanceSpec.getAppId().getName()
                                      + instanceSpec.getAppId().getVersion()
                                      + UUID.randomUUID());
            val hostConfig = new HostConfig()
                    .withMemorySwappiness(0L)
                    .withOomKillDisable(true)
                    .withAutoRemove(true)/*
                    .withPublishAllPorts(true)*/;

            instanceSpec.getResources()
                    .forEach(resourceRequirement -> resourceRequirement.accept(new ResourceRequirementVisitor<Void>() {
                        @Override
                        public Void visit(CPURequirement cpuRequirement) {
                            hostConfig.withCpuCount(cpuRequirement.getCount());
                            return null;
                        }

                        @Override
                        public Void visit(MemoryRequirement memoryRequirement) {
                            hostConfig.withMemory(memoryRequirement.getSizeInMB() * (1<<20));
                            return null;
                        }
                    }));
            val ports = new Ports();
            val env = new ArrayList<String>();
            val portMappings = new HashMap<String, InstancePort>();
            env.add("HOST=" + InetAddress.getLocalHost().getHostName());
            instanceSpec.getPorts().forEach(
                    portSpec -> {
                        val freePort = findFreePort();
                        val specPort = portSpec.getPort();
                        ports.bind(new ExposedPort(specPort), Ports.Binding.bindPort(freePort));
                        env.add(String.format("PORT_%d=%d", specPort, freePort));
                        portMappings.put(portSpec.getName(), new InstancePort(portSpec.getPort(), freePort));
                    });
            hostConfig.withPortBindings(ports);

            val instanceInfo = instanceInfo(instanceSpec, portMappings);
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
                    .exec(new ResultCallback.Adapter<Frame>() {

                        @Override
                        public void onNext(Frame object) {
                            switch (object.getStreamType())  {
                                case STDOUT:
                                    log.info(new String(object.getPayload(), Charset.defaultCharset()));
                                    break;
                                case STDERR:
                                    log.error(new String(object.getPayload(), Charset.defaultCharset()));
                                    break;
                                case STDIN:
                                case RAW:
                                default:
                                    break;
                            }
                        }
                    });
            return StateData.create(InstanceState.UNREADY,
                                    instanceInfo);
        }
        catch (Exception e) {
            log.error("Error creating container: ", e);
            return StateData.errorFrom(currentState, InstanceState.START_FAILED, e.getMessage());
        }
    }

    private InstanceInfo instanceInfo(InstanceSpec instanceSpec, HashMap<String, InstancePort> portMappings) {
        return new InstanceInfo(instanceSpec.getAppId(),
                                instanceSpec.getInstanceId(),
                                "",
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
