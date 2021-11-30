package com.phonepe.drove.executor;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.google.common.collect.ImmutableList;
import com.phonepe.drove.common.model.InstanceSpec;
import com.phonepe.drove.common.model.executor.ExecutorAddress;
import com.phonepe.drove.executor.model.ExecutorInstanceInfo;
import com.phonepe.drove.models.application.PortType;
import com.phonepe.drove.models.application.checks.CheckModeSpec;
import com.phonepe.drove.models.info.resources.allocation.CPUAllocation;
import com.phonepe.drove.models.info.resources.allocation.MemoryAllocation;
import com.phonepe.drove.models.application.PortSpec;
import com.phonepe.drove.models.application.checks.CheckSpec;
import com.phonepe.drove.models.application.checks.HTTPCheckModeSpec;
import com.phonepe.drove.models.application.executable.DockerCoordinates;
import com.phonepe.drove.models.common.HTTPVerb;
import com.phonepe.drove.models.instance.InstancePort;
import com.phonepe.drove.models.instance.LocalInstanceInfo;
import io.dropwizard.util.Duration;
import lombok.experimental.UtilityClass;

import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.UUID;

/**
 *
 */
@UtilityClass
public class TestingUtils {
    public static InstanceSpec testSpec() {
        return new InstanceSpec("T001",
                                UUID.randomUUID().toString(),
                                new DockerCoordinates(
                                        "docker.io/santanusinha/test-service:0.1",
                                        Duration.seconds(100)),
                                ImmutableList.of(new CPUAllocation(Collections.singletonMap(0, Collections.singleton(1))),
                                                 new MemoryAllocation(Collections.singletonMap(0, 512L))),
                                Collections.singletonList(new PortSpec("main", 3000, PortType.HTTP)),
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
                                Collections.emptyMap());
    }

    public static ExecutorAddress localAddress() {
        return new ExecutorAddress(UUID.randomUUID().toString(), "localhost", 3000);
    }

    public static ExecutorInstanceInfo createExecutorInfo(WireMockRuntimeInfo wm) {
        return new ExecutorInstanceInfo("TEST_APP",
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
}
