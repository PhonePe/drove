package com.phonepe.drove.executor.recovery;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientImpl;
import com.github.dockerjava.zerodep.ZerodepDockerHttpClient;
import com.phonepe.drove.common.StateData;
import com.phonepe.drove.executor.engine.DockerLabels;
import com.phonepe.drove.executor.statemachine.InstanceStateMachine;
import com.phonepe.drove.models.instance.InstanceInfo;
import com.phonepe.drove.models.instance.InstanceState;
import lombok.val;

import java.net.URI;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 *
 */
public class InstanceRecovery {
    private final ObjectMapper mapper;

    public InstanceRecovery(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    List<InstanceStateMachine> recoverState() {
        val client = DockerClientImpl.getInstance(DefaultDockerClientConfig.createDefaultConfigBuilder()
                                                          .build(),
                                                  new ZerodepDockerHttpClient.Builder()
                                                          .dockerHost(URI.create("unix:///var/run/docker.sock"))
                                                          .build());
        val containers = client.listContainersCmd()
                .withLabelFilter(Collections.singletonList(DockerLabels.DROVE_INSTANCE_LABEL))
                .exec();
        return containers.stream()
                .map(container -> {
                    var instanceInfo = container.getLabels().get("drove.instance");
                    try {
                        return new InstanceStateMachine(
                                null,
                                StateData.create(InstanceState.UNKNOWN,
                                                 mapper.readValue(container.getLabels()
                                                                          .get(DockerLabels.DROVE_INSTANCE_LABEL),
                                                                  InstanceInfo.class)));
                    }
                    catch (JsonProcessingException e) {
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toUnmodifiableList());

    }
}
