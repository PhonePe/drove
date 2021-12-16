package com.phonepe.drove.executor;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.google.common.collect.ImmutableList;
import com.phonepe.drove.common.model.InstanceSpec;
import com.phonepe.drove.common.model.MessageDeliveryStatus;
import com.phonepe.drove.common.model.MessageHeader;
import com.phonepe.drove.common.model.executor.ExecutorAddress;
import com.phonepe.drove.common.model.executor.StartInstanceMessage;
import com.phonepe.drove.common.model.executor.StopInstanceMessage;
import com.phonepe.drove.executor.engine.InstanceEngine;
import com.phonepe.drove.executor.model.ExecutorInstanceInfo;
import com.phonepe.drove.models.application.PortSpec;
import com.phonepe.drove.models.application.PortType;
import com.phonepe.drove.models.application.checks.CheckModeSpec;
import com.phonepe.drove.models.application.checks.CheckSpec;
import com.phonepe.drove.models.application.checks.HTTPCheckModeSpec;
import com.phonepe.drove.models.application.executable.DockerCoordinates;
import com.phonepe.drove.models.application.logging.LocalLoggingSpec;
import com.phonepe.drove.models.common.HTTPVerb;
import com.phonepe.drove.models.info.resources.allocation.CPUAllocation;
import com.phonepe.drove.models.info.resources.allocation.MemoryAllocation;
import com.phonepe.drove.models.instance.InstanceInfo;
import com.phonepe.drove.models.instance.InstancePort;
import com.phonepe.drove.models.instance.LocalInstanceInfo;
import io.dropwizard.util.Duration;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

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
public class TestingUtils {
    public static final String IMAGE_NAME = "docker.io/santanusinha/perf-test-server:0.1";

    public static InstanceSpec testSpec() {
        return new InstanceSpec("T001",
                                "TEST_SPEC",
                                UUID.randomUUID().toString(),
                                new DockerCoordinates(TestingUtils.IMAGE_NAME,
                                        Duration.seconds(100)),
                                ImmutableList.of(new CPUAllocation(Collections.singletonMap(0, Set.of(2, 3))),
                                                 new MemoryAllocation(Collections.singletonMap(0, 512L))),
                                Collections.singletonList(new PortSpec("main", 8000, PortType.HTTP)),
                                Collections.emptyList(),
                                new CheckSpec(new HTTPCheckModeSpec("http",
                                                                    "main",
                                                                    "/",
                                                                    HTTPVerb.GET,
                                                                    Collections.singleton(200),
                                                                    "",
                                                                    Duration.seconds(1)),
                                              Duration.seconds(1),
                                              Duration.seconds(3),
                                              3,
                                              Duration.seconds(0)),
                                new CheckSpec(new HTTPCheckModeSpec("http",
                                                             "main",
                                                             "/",
                                                             HTTPVerb.GET,
                                                             Collections.singleton(200),
                                                             "",
                                                             Duration.seconds(1)),
                                       Duration.seconds(1),
                                       Duration.seconds(3),
                                       3,
                                       Duration.seconds(0)),
                                LocalLoggingSpec.DEFAULT,
                                Collections.emptyMap());
    }

    public static ExecutorAddress localAddress() {
        return new ExecutorAddress(UUID.randomUUID().toString(), "localhost", 3000);
    }

    public static ExecutorInstanceInfo createExecutorInfo(WireMockRuntimeInfo wm) {
        return new ExecutorInstanceInfo("TEST_APP_1",
                                        "TEST_APP",
                                        "TEST_INSTANCE",
                                        "TEST_EXEC",
                                        new LocalInstanceInfo("localhost",
                                                              Collections.singletonMap(
                                                                          "main",
                                                                          new InstancePort(
                                                                                  8080,
                                                                                  wm.getHttpPort(),
                                                                                  PortType.HTTP))),
                                        List.of(new CPUAllocation(Collections.singletonMap(1,
                                                                                           Collections.singleton(0))),
                                                new MemoryAllocation(Collections.singletonMap(0, 512L))),
                                        Collections.emptyMap(),
                                        new Date(),
                                        new Date());
    }

    public static HTTPCheckModeSpec httpCheck(HTTPVerb verb) {
        return httpCheck(verb, null);
    }

    public static HTTPCheckModeSpec httpCheck(HTTPVerb verb, String body) {
        return new HTTPCheckModeSpec("http",
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

    public static<R> R executeOnceContainerStarted(final InstanceEngine engine, final Function<InstanceInfo, R> check) {
        val spec = TestingUtils.testSpec();
        val instanceId = spec.getInstanceId();
        val executorAddress = new ExecutorAddress("eid", "localhost", 3000);
        val startInstanceMessage = new StartInstanceMessage(MessageHeader.controllerRequest(),
                                                            executorAddress,
                                                            spec);
        val startResponse = engine.handleMessage(startInstanceMessage);
        try {
            assertEquals(MessageDeliveryStatus.ACCEPTED, startResponse.getStatus());
            assertEquals(MessageDeliveryStatus.FAILED, engine.handleMessage(startInstanceMessage).getStatus());
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
            assertEquals(MessageDeliveryStatus.ACCEPTED, engine.handleMessage(stopInstanceMessage).getStatus());
            waitUntil(() -> engine.currentState(instanceId).isEmpty());
        }
    }
}
