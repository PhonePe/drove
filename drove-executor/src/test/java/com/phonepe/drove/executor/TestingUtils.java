package com.phonepe.drove.executor;

import com.google.common.collect.ImmutableList;
import com.phonepe.drove.common.model.InstanceSpec;
import com.phonepe.drove.common.model.executor.ExecutorAddress;
import com.phonepe.drove.models.application.PortType;
import com.phonepe.drove.models.info.resources.allocation.CPUAllocation;
import com.phonepe.drove.models.info.resources.allocation.MemoryAllocation;
import com.phonepe.drove.models.application.PortSpec;
import com.phonepe.drove.models.application.checks.CheckSpec;
import com.phonepe.drove.models.application.checks.HTTPCheckModeSpec;
import com.phonepe.drove.models.application.executable.DockerCoordinates;
import com.phonepe.drove.models.common.HTTPVerb;
import io.dropwizard.util.Duration;
import lombok.experimental.UtilityClass;

import java.util.Collections;
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

}
