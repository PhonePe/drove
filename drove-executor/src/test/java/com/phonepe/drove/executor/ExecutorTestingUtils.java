package com.phonepe.drove.executor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.model.HostConfig;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientImpl;
import com.github.dockerjava.zerodep.ZerodepDockerHttpClient;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.google.common.collect.ImmutableList;
import com.phonepe.drove.common.CommonTestUtils;
import com.phonepe.drove.common.model.ApplicationInstanceSpec;
import com.phonepe.drove.common.model.MessageDeliveryStatus;
import com.phonepe.drove.common.model.MessageHeader;
import com.phonepe.drove.common.model.TaskInstanceSpec;
import com.phonepe.drove.common.model.executor.ExecutorAddress;
import com.phonepe.drove.common.model.executor.StartInstanceMessage;
import com.phonepe.drove.common.model.executor.StopInstanceMessage;
import com.phonepe.drove.executor.engine.ApplicationInstanceEngine;
import com.phonepe.drove.executor.engine.DockerLabels;
import com.phonepe.drove.executor.engine.ExecutorMessageHandler;
import com.phonepe.drove.executor.engine.TaskInstanceEngine;
import com.phonepe.drove.executor.model.ExecutorInstanceInfo;
import com.phonepe.drove.executor.resourcemgmt.ResourceConfig;
import com.phonepe.drove.models.application.*;
import com.phonepe.drove.models.application.checks.CheckModeSpec;
import com.phonepe.drove.models.application.checks.CheckSpec;
import com.phonepe.drove.models.application.checks.HTTPCheckModeSpec;
import com.phonepe.drove.models.application.executable.DockerCoordinates;
import com.phonepe.drove.models.application.logging.LocalLoggingSpec;
import com.phonepe.drove.models.common.HTTPVerb;
import com.phonepe.drove.models.info.nodedata.NodeTransportType;
import com.phonepe.drove.models.info.resources.allocation.CPUAllocation;
import com.phonepe.drove.models.info.resources.allocation.MemoryAllocation;
import com.phonepe.drove.models.instance.InstanceInfo;
import com.phonepe.drove.models.instance.InstancePort;
import com.phonepe.drove.models.instance.LocalInstanceInfo;
import io.dropwizard.util.Duration;
import lombok.SneakyThrows;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

import java.net.URI;
import java.util.*;
import java.util.function.Function;

import static com.phonepe.drove.common.CommonTestUtils.waitUntil;
import static com.phonepe.drove.models.instance.InstanceState.HEALTHY;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 *
 */
@Slf4j
@UtilityClass
public class ExecutorTestingUtils {
    public static final String EXECUTOR_ID = "TEST_EXEC";
    public static final DockerClient DOCKER_CLIENT
            = DockerClientImpl.getInstance(DefaultDockerClientConfig.createDefaultConfigBuilder().build(),
                                           new ZerodepDockerHttpClient.Builder()
                                                   .dockerHost(URI.create("unix:///var/run/docker.sock"))
                                                   .build());

    public static ApplicationInstanceSpec testAppInstanceSpec() {
        return testAppInstanceSpec(CommonTestUtils.APP_IMAGE_NAME);
    }

    public static ApplicationInstanceSpec testAppInstanceSpec(final String imageName) {
        return testAppInstanceSpec(imageName, 3);
    }

    public static ApplicationInstanceSpec testAppInstanceSpec(final String imageName, int attempt) {
        return new ApplicationInstanceSpec("T001",
                                           "TEST_SPEC",
                                           UUID.randomUUID().toString(),
                                           new DockerCoordinates(imageName, Duration.seconds(100)),
                                           ImmutableList.of(new CPUAllocation(Collections.singletonMap(0,
                                                                                                       Set.of(2, 3))),
                                                            new MemoryAllocation(Collections.singletonMap(0, 512L))),
                                           Collections.singletonList(new PortSpec("main", 8000, PortType.HTTP)),
                                           List.of(new MountedVolume("/tmp",
                                                                     "/tmp",
                                                                     MountedVolume.MountMode.READ_ONLY)),
                                           new CheckSpec(new HTTPCheckModeSpec(HTTPCheckModeSpec.Protocol.HTTP,
                                                                               "main",
                                                                               "/",
                                                                               HTTPVerb.GET,
                                                                               Collections.singleton(200),
                                                                               "",
                                                                               Duration.seconds(1)),
                                                         Duration.seconds(1),
                                                         Duration.seconds(3),
                                                         attempt,
                                                         Duration.seconds(0)),
                                           new CheckSpec(new HTTPCheckModeSpec(HTTPCheckModeSpec.Protocol.HTTP,
                                                                               "main",
                                                                               "/",
                                                                               HTTPVerb.GET,
                                                                               Collections.singleton(200),
                                                                               "",
                                                                               Duration.seconds(1)),
                                                         Duration.seconds(1),
                                                         Duration.seconds(3),
                                                         attempt,
                                                         Duration.seconds(1)),
                                           LocalLoggingSpec.DEFAULT,
                                           Collections.emptyMap(),
                                           new PreShutdownSpec(List.of(new HTTPCheckModeSpec(HTTPCheckModeSpec.Protocol.HTTP,
                                                                                             "main",
                                                                                             "/",
                                                                                             HTTPVerb.GET,
                                                                                             Collections.singleton(200),
                                                                                             "",
                                                                                             Duration.seconds(1))),
                                                               Duration.seconds(1)),
                                           "TestToken");
    }

    public static TaskInstanceSpec testTaskInstanceSpec() {
        return testTaskInstanceSpec(CommonTestUtils.TASK_IMAGE_NAME, Map.of("ITERATIONS", "3"));
    }


    public static TaskInstanceSpec testTaskInstanceSpec(Map<String, String> env) {
        return testTaskInstanceSpec(CommonTestUtils.TASK_IMAGE_NAME, env);
    }

    public static TaskInstanceSpec testTaskInstanceSpec(final String imageName, Map<String, String> env) {
        return new TaskInstanceSpec("T001",
                                           "TEST_SPEC",
                                           UUID.randomUUID().toString(),
                                           new DockerCoordinates(imageName, Duration.seconds(100)),
                                           ImmutableList.of(new CPUAllocation(Collections.singletonMap(0,
                                                                                                       Set.of(2, 3))),
                                                            new MemoryAllocation(Collections.singletonMap(0, 512L))),
                                           List.of(new MountedVolume("/tmp",
                                                                     "/tmp",
                                                                     MountedVolume.MountMode.READ_ONLY)),
                                           LocalLoggingSpec.DEFAULT,
                                           env);
    }

    public static ExecutorAddress localAddress() {
        return new ExecutorAddress(UUID.randomUUID().toString(), "localhost", 3000, NodeTransportType.HTTP);
    }

    public static ExecutorInstanceInfo createExecutorInfo(WireMockRuntimeInfo wm) {
        return createExecutorInfo(testAppInstanceSpec(CommonTestUtils.APP_IMAGE_NAME), wm);
    }

    public static ExecutorInstanceInfo createExecutorInfo(ApplicationInstanceSpec spec, WireMockRuntimeInfo wm) {
        return createExecutorInfo(spec, wm.getHttpPort());
    }

    public static ExecutorInstanceInfo createExecutorInfo(ApplicationInstanceSpec spec, int port) {
        return new ExecutorInstanceInfo(spec.getAppId(),
                                        spec.getAppName(),
                                        spec.getInstanceId(),
                                        EXECUTOR_ID,
                                        new LocalInstanceInfo("localhost",
                                                              Collections.singletonMap(
                                                                      "main",
                                                                      new InstancePort(
                                                                              8080,
                                                                              port,
                                                                              PortType.HTTP))),
                                        spec.getResources(),
                                        spec.getEnv(),
                                        new Date(),
                                        new Date());
    }

    public static HTTPCheckModeSpec httpCheck(HTTPVerb verb) {
        return httpCheck(verb, null);
    }

    public static HTTPCheckModeSpec httpCheck(HTTPVerb verb, String body) {
        return new HTTPCheckModeSpec(HTTPCheckModeSpec.Protocol.HTTP,
                                     "main",
                                     "/",
                                     verb,
                                     Collections.singleton(200),
                                     body,
                                     Duration.seconds(1));
    }

    public static CheckSpec checkSpec(HTTPVerb verb) {
        return checkSpec(verb, null);
    }

    public static CheckSpec checkSpec(HTTPVerb verb, String body) {
        return checkSpec(httpCheck(verb, body));
    }

    public static CheckSpec checkSpec(final CheckModeSpec checkModeSpec) {
        return new CheckSpec(checkModeSpec,
                             Duration.seconds(1),
                             Duration.seconds(1),
                             3,
                             Duration.seconds(0));
    }

    public static <R> R executeOnceContainerStarted(
            final ApplicationInstanceEngine engine,
            final TaskInstanceEngine taskInstanceEngine,
            final Function<InstanceInfo, R> check) {
        val spec = ExecutorTestingUtils.testAppInstanceSpec();
        val instanceId = spec.getInstanceId();
        val executorAddress = new ExecutorAddress("eid", "localhost", 3000, NodeTransportType.HTTP);
        val startInstanceMessage = new StartInstanceMessage(MessageHeader.controllerRequest(),
                                                            executorAddress,
                                                            spec);
        val messageHandler = new ExecutorMessageHandler(engine, taskInstanceEngine, null);
        val startResponse = startInstanceMessage.accept(messageHandler);
        try {
            assertEquals(MessageDeliveryStatus.ACCEPTED, startResponse.getStatus());
            assertEquals(MessageDeliveryStatus.FAILED, startInstanceMessage.accept(messageHandler).getStatus());
            waitUntil(() -> engine.currentState(instanceId)
                    .map(InstanceInfo::getState)
                    .map(instanceState -> instanceState.equals(HEALTHY))
                    .orElse(false));
            val info = engine.currentState(instanceId).orElse(null);
            assertNotNull(info);
            return check.apply(info);
        }
        finally {
            val stopInstanceMessage = new StopInstanceMessage(MessageHeader.controllerRequest(),
                                                              executorAddress,
                                                              instanceId);
            assertEquals(MessageDeliveryStatus.ACCEPTED, stopInstanceMessage.accept(messageHandler).getStatus());
            waitUntil(() -> engine.currentState(instanceId).isEmpty());
        }
    }

    public static ResourceConfig resourceConfig() {
        return new ResourceConfig()
                .setOsCores(Set.of(0, 1))
                .setExposedMemPercentage(90)
                .setTags(Set.of("test-machine"));
    }

    @SneakyThrows
    public static void startTestContainer(
            ApplicationInstanceSpec spec,
            ExecutorInstanceInfo instanceData,
            ObjectMapper mapper) {
        String containerId;
        val createContainerResponse = DOCKER_CLIENT
                .createContainerCmd(CommonTestUtils.APP_IMAGE_NAME)
                .withName("RecoveryTest")
                .withLabels(Map.of(
                        DockerLabels.DROVE_JOB_TYPE_LABEL,
                        JobType.SERVICE.name(),
                        DockerLabels.DROVE_INSTANCE_ID_LABEL,
                        spec.getInstanceId(),
                        DockerLabels.DROVE_INSTANCE_SPEC_LABEL,
                        mapper.writeValueAsString(spec),
                        DockerLabels.DROVE_INSTANCE_DATA_LABEL,
                        mapper.writeValueAsString(instanceData)))
                .withHostConfig(new HostConfig()
                                        .withAutoRemove(true))
                .exec();
        containerId = createContainerResponse.getId();
        DOCKER_CLIENT.startContainerCmd(containerId)
                .exec();
    }
}
