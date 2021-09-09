package com.phonepe.drove.executor;

import com.phonepe.drove.internalmodels.InstanceSpec;
import com.phonepe.drove.models.application.AppId;
import com.phonepe.drove.models.application.PortSpec;
import com.phonepe.drove.models.application.checks.CheckSpec;
import com.phonepe.drove.models.application.checks.HTTPCheckModeSpec;
import com.phonepe.drove.models.application.executable.DockerCoordinates;
import com.phonepe.drove.models.application.requirements.CPURequirement;
import com.phonepe.drove.models.application.requirements.MemoryRequirement;
import com.phonepe.drove.models.common.HTTPVerb;
import io.dropwizard.util.Duration;
import lombok.experimental.UtilityClass;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 *
 */
@UtilityClass
public class TestingUtils {
    public static InstanceSpec testSpec() {
        return new InstanceSpec(new AppId("test", 1),
                                UUID.randomUUID().toString(),
                                new DockerCoordinates(
                                        "docker.io/santanusinha/test-service:0.1",
                                        Duration.seconds(100)),
                                List.of(new CPURequirement(1),
                                        new MemoryRequirement(512)),
                                Collections.singletonList(new PortSpec("main", 3000)),
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

}
