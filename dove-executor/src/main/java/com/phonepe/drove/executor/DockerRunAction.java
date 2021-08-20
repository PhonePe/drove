package com.phonepe.drove.executor;

import com.github.dockerjava.api.model.ExposedPort;
import com.github.dockerjava.api.model.HostConfig;
import com.github.dockerjava.api.model.Ports;
import com.phonepe.drove.common.StateData;
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
import java.util.*;

/**
 *
 */
@Slf4j
public class DockerRunAction extends InstanceAction {
    @Override
    public StateData<InstanceState, InstanceInfo> execute(
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
//                            hostConfig.withMemory(memoryRequirement.getSizeInMB() * (2^20));
                            return null;
                        }
                    }));
            val ports = new Ports();
            val portEnv = new ArrayList<String>();
            val portMappings = new HashMap<String, InstancePort>();
            portEnv.add("HOST=" + InetAddress.getLocalHost().getHostName());
            instanceSpec.getPorts().forEach(
                    portSpec -> {
                        val freePort = findFreePort();
                        val specPort = portSpec.getPort();
                        ports.bind(new ExposedPort(specPort), Ports.Binding.bindPort(freePort));
                        portEnv.add(String.format("PORT_%d=%d", specPort, freePort));
                        portMappings.put(portSpec.getName(), new InstancePort(portSpec.getPort(), freePort));
                    });
            hostConfig.withPortBindings(ports);
            val id = containerCmd
                    .withHostConfig(hostConfig)
                    .withEnv(portEnv)
                    .exec()
                    .getId();
            log.debug("Created container id: {}", id);
            context.setDockerInstanceId(id);
            client.startContainerCmd(id)
                    .exec();
            return StateData.create(InstanceState.UNREADY,
                                   new InstanceInfo(instanceSpec.getAppId(),
                                                    "id",
                                                    "",
                                                    InstanceState.UNREADY,
                                                    portMappings,
                                                    Collections.emptyMap(),
                                                    new Date(),
                                                    new Date()));
        }
        catch (Exception e) {
            log.error("Error creating container: ", e);
            return StateData.errorFrom(currentState, InstanceState.START_FAILED, e.getMessage());
        }
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
