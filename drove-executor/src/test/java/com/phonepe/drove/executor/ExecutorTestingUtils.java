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

package com.phonepe.drove.executor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.exception.NotFoundException;
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
import com.phonepe.drove.executor.model.ExecutorTaskInfo;
import com.phonepe.drove.executor.resourcemgmt.ResourceConfig;
import com.phonepe.drove.executor.resourcemgmt.ResourceManager;
import com.phonepe.drove.executor.statemachine.InstanceActionContext;
import com.phonepe.drove.executor.utils.ExecutorUtils;
import com.phonepe.drove.models.application.*;
import com.phonepe.drove.models.application.checks.CheckModeSpec;
import com.phonepe.drove.models.application.checks.CheckSpec;
import com.phonepe.drove.models.application.checks.CmdCheckModeSpec;
import com.phonepe.drove.models.application.checks.HTTPCheckModeSpec;
import com.phonepe.drove.models.application.devices.DirectDeviceSpec;
import com.phonepe.drove.models.application.executable.DockerCoordinates;
import com.phonepe.drove.models.application.logging.LocalLoggingSpec;
import com.phonepe.drove.models.common.HTTPVerb;
import com.phonepe.drove.models.common.Protocol;
import com.phonepe.drove.models.config.impl.InlineConfigSpec;
import com.phonepe.drove.models.info.nodedata.NodeTransportType;
import com.phonepe.drove.models.info.resources.allocation.CPUAllocation;
import com.phonepe.drove.models.info.resources.allocation.MemoryAllocation;
import com.phonepe.drove.models.instance.InstanceInfo;
import com.phonepe.drove.models.instance.InstancePort;
import com.phonepe.drove.models.instance.LocalInstanceInfo;
import io.dropwizard.util.Duration;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

import java.net.URI;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static com.phonepe.drove.common.CommonTestUtils.base64;
import static com.phonepe.drove.common.CommonTestUtils.waitUntil;
import static com.phonepe.drove.executor.utils.DockerUtils.runCommandInContainer;
import static com.phonepe.drove.models.instance.InstanceState.HEALTHY;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 *
 */
@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
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
        return testAppInstanceSpec(imageName, attempt, false);
    }

    public static ApplicationInstanceSpec testAppInstanceSpec(final String imageName, int attempt, boolean useHttps) {
        val protocol = useHttps ? Protocol.HTTPS : Protocol.HTTP;
        val portType = useHttps ? PortType.HTTPS : PortType.HTTP;
        return new ApplicationInstanceSpec("T001",
                                           "TEST_SPEC",
                                           UUID.randomUUID().toString(),
                                           new DockerCoordinates(imageName, Duration.seconds(100)),
                                           ImmutableList.of(new CPUAllocation(Collections.singletonMap(0,
                                                                                                       Set.of(2, 3))),
                                                            new MemoryAllocation(Collections.singletonMap(0, 512L))),
                                           Collections.singletonList(new PortSpec("main", 8000, portType)),
                                           List.of(new MountedVolume("/tmp",
                                                                     "/tmp",
                                                                     MountedVolume.MountMode.READ_ONLY)),
                                           List.of(new InlineConfigSpec("/files/drove.txt", base64("Drove Test"))),
                                           new CheckSpec(new HTTPCheckModeSpec(protocol,
                                                                               "main",
                                                                               "/",
                                                                               HTTPVerb.GET,
                                                                               Collections.singleton(200),
                                                                               "",
                                                                               Duration.seconds(1),
                                                                               useHttps),
                                                         Duration.seconds(1),
                                                         Duration.seconds(3),
                                                         attempt,
                                                         Duration.seconds(0)),
                                           new CheckSpec(new HTTPCheckModeSpec(protocol,
                                                                               "main",
                                                                               "/",
                                                                               HTTPVerb.GET,
                                                                               Collections.singleton(200),
                                                                               "",
                                                                               Duration.seconds(1),
                                                                               useHttps),
                                                         Duration.seconds(1),
                                                         Duration.seconds(3),
                                                         attempt,
                                                         Duration.seconds(1)),
                                           LocalLoggingSpec.DEFAULT,
                                           Collections.emptyMap(),
                                           imageName.equals(CommonTestUtils.APP_IMAGE_NAME)
                                           ? List.of("./entrypoint.sh", "arg1", "arg2")
                                           : null,
                                           List.of(DirectDeviceSpec.builder()
                                                           .pathOnHost("/dev/random")
                                                           .pathInContainer("/dev/random")
                                                           .permissions(DirectDeviceSpec.DirectDevicePermissions.ALL)
                                                           .build()),
                                           new PreShutdownSpec(List.of(new HTTPCheckModeSpec(protocol,
                                                                                             "main",
                                                                                             "/",
                                                                                             HTTPVerb.GET,
                                                                                             Collections.singleton(200),
                                                                                             "",
                                                                                             Duration.seconds(1),
                                                                                             useHttps),
                                                                       new CmdCheckModeSpec("echo -n 1"),
                                                                       new CmdCheckModeSpec("SomeWrongCommand")
                                                                      ),
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
                                    "TEST_TASK_SPEC",
                                    UUID.randomUUID().toString(),
                                    new DockerCoordinates(imageName, Duration.seconds(100)),
                                    ImmutableList.of(new CPUAllocation(Collections.singletonMap(0,
                                                                                                Set.of(2, 3))),
                                                     new MemoryAllocation(Collections.singletonMap(0, 512L))),
                                    List.of(new MountedVolume("/tmp",
                                                              "/tmp",
                                                              MountedVolume.MountMode.READ_ONLY)),
                                    List.of(new InlineConfigSpec("/files/drove.txt", base64("Drove Test"))),
                                    LocalLoggingSpec.DEFAULT,
                                    env,
                                    null,
                                    List.of(DirectDeviceSpec.builder()
                                                    .pathOnHost("/dev/random")
                                                    .build()));
    }

    public static ExecutorAddress localAddress() {
        return new ExecutorAddress(UUID.randomUUID().toString(), "localhost", 3000, NodeTransportType.HTTP);
    }

    public static ExecutorInstanceInfo createExecutorAppInstanceInfo(WireMockRuntimeInfo wm) {
        return createExecutorAppInstanceInfo(testAppInstanceSpec(CommonTestUtils.APP_IMAGE_NAME), wm);
    }

    public static ExecutorInstanceInfo createExecutorAppInstanceInfo(
            ApplicationInstanceSpec spec,
            WireMockRuntimeInfo wm) {
        return createExecutorAppInstanceInfo(spec, wm.getHttpPort());
    }

    public static ExecutorInstanceInfo createExecutorAppInstanceInfo(ApplicationInstanceSpec spec, int port) {
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

    public static ExecutorTaskInfo createExecutorTaskInfo(TaskInstanceSpec spec) {
        return new ExecutorTaskInfo(spec.getTaskId(),
                                    spec.getSourceAppName(),
                                    spec.getInstanceId(),
                                    EXECUTOR_ID,
                                    "localhost",
                                    spec.getExecutable(),
                                    spec.getResources(),
                                    spec.getVolumes(),
                                    spec.getLoggingSpec(),
                                    spec.getEnv(),
                                    Map.of(),
                                    null,
                                    new Date(),
                                    new Date());
    }

    public static HTTPCheckModeSpec httpCheck(HTTPVerb verb) {
        return httpCheck(verb, null, false);
    }

    public static HTTPCheckModeSpec httpCheck(HTTPVerb verb, String body, boolean https) {
        return new HTTPCheckModeSpec(https ? Protocol.HTTPS : Protocol.HTTP,
                                     "main",
                                     "/",
                                     verb,
                                     Collections.singleton(200),
                                     body,
                                     Duration.seconds(1),
                                     https); //Needs to skip cert etc validation for tests
    }

    public static CheckSpec checkSpec(HTTPVerb verb) {
        return checkSpec(verb, false);
    }

    public static CheckSpec checkSpec(HTTPVerb verb, boolean https) {
        return checkSpec(verb, null, https);
    }

    public static CheckSpec checkSpec(HTTPVerb verb, String body) {
        return checkSpec(verb, body, false);
    }

    public static CheckSpec checkSpec(HTTPVerb verb, String body, boolean https) {
        return checkSpec(httpCheck(verb, body, https));
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
    public static String startTestAppContainer(
            ApplicationInstanceSpec spec,
            ExecutorInstanceInfo instanceData,
            ObjectMapper mapper) {
        String containerId;
        try (val create = DOCKER_CLIENT.createContainerCmd(CommonTestUtils.APP_IMAGE_NAME)) {
            val createContainerResponse = create
                    .withName("AppRecoveryTest")
                    .withLabels(Map.of(
                            DockerLabels.DROVE_JOB_TYPE_LABEL,
                            JobType.SERVICE.name(),
                            DockerLabels.DROVE_INSTANCE_ID_LABEL,
                            spec.getInstanceId(),
                            DockerLabels.DROVE_INSTANCE_SPEC_LABEL,
                            mapper.writeValueAsString(spec),
                            DockerLabels.DROVE_INSTANCE_DATA_LABEL,
                            mapper.writeValueAsString(instanceData)))
                    .withHostConfig(new HostConfig().withAutoRemove(true))
                    .exec();
            containerId = createContainerResponse.getId();
            try (val start = DOCKER_CLIENT.startContainerCmd(containerId)) {
                start.exec();
                return containerId;
            }
        }
    }

    @SneakyThrows
    public static String startTestTaskContainer(
            TaskInstanceSpec spec,
            ExecutorTaskInfo instanceData,
            ObjectMapper mapper) {
        String containerId;
        try (val create = DOCKER_CLIENT
                .createContainerCmd(CommonTestUtils.TASK_IMAGE_NAME)) {
            val createContainerResponse = create
                    .withName("TaskRecoveryTest")
                    .withLabels(Map.of(
                            DockerLabels.DROVE_JOB_TYPE_LABEL,
                            JobType.COMPUTATION.name(),
                            DockerLabels.DROVE_INSTANCE_ID_LABEL,
                            spec.getTaskId(),
                            DockerLabels.DROVE_INSTANCE_SPEC_LABEL,
                            mapper.writeValueAsString(spec),
                            DockerLabels.DROVE_INSTANCE_DATA_LABEL,
                            mapper.writeValueAsString(instanceData)))
                    .withHostConfig(new HostConfig().withAutoRemove(true))
                    .exec();
            containerId = createContainerResponse.getId();
            try (val start = DOCKER_CLIENT.startContainerCmd(containerId)) {
                start.exec();
                return containerId;
            }
        }
    }

    public static boolean containerExists(String containerId) {
        try (val inspect = DOCKER_CLIENT.inspectContainerCmd(containerId)) {
            val res = inspect.exec();
            return res != null && Objects.requireNonNullElse(res.getState().getRunning(), false);
        }
        catch (NotFoundException e) {
            return false;
        }
    }

    @SneakyThrows
    public static String runCmd(InstanceActionContext<ApplicationInstanceSpec> ctx, String cmd) {
        return runCommandInContainer(ctx.getDockerInstanceId(), DOCKER_CLIENT, cmd).getOutput();
    }

    public static ResourceManager.NodeInfo numaNode() {
        return ResourceManager.NodeInfo.from(Set.of(1, 2, 3, 4), 1000);
    }

    public static ResourceManager.NodeInfo discoveredNumaNode() {
        return discoveredNumaNode(1);
    }

    public static ResourceManager.NodeInfo discoveredNumaNode(int base) {
        return discoveredNumaNode(base, 4);
    }

    public static ResourceManager.NodeInfo discoveredNumaNode(int base, int count) {
        val cores = IntStream.range(base, base + count).boxed().collect(Collectors.toUnmodifiableSet());
        return new ResourceManager.NodeInfo(cores, ExecutorUtils.mapCores(cores), 1000, cores, 1000);
    }
}
