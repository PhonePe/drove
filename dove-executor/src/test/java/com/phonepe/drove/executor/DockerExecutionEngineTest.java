package com.phonepe.drove.executor;

import com.google.common.collect.ImmutableList;
import com.phonepe.drove.internalmodels.InstanceSpec;
import com.phonepe.drove.models.application.AppId;
import com.phonepe.drove.models.application.PortSpec;
import com.phonepe.drove.models.application.executable.DockerCoordinates;
import com.phonepe.drove.models.application.requirements.CPURequirement;
import com.phonepe.drove.models.application.requirements.MemoryRequirement;
import io.dropwizard.util.Duration;
import lombok.val;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.UUID;

/**
 *
 */
class DockerExecutionEngineTest {
    @Test
    void basic() {
        val e = new DockerExecutionEngine();
        e.startContainer(new InstanceSpec(new AppId("test", 1),
                                          UUID.randomUUID().toString(),
                                          new DockerCoordinates("docker.io/santanusinha/test-service:0.1",
                                                                        Duration.seconds(100)),
                                          ImmutableList.of(new CPURequirement(1),
                                                                   new MemoryRequirement(512)),
                                          Collections.singletonList(new PortSpec("main", 3000)),
                                          Collections.emptyList(),
                                          null,
                                          null,
                                          Collections.emptyMap()),
                         null);
    }

}