package com.phonepe.drove.executor;

import com.google.common.collect.ImmutableList;
import com.phonepe.drove.common.model.InstanceSpec;
import com.phonepe.drove.models.info.resources.allocation.CPUAllocation;
import com.phonepe.drove.models.info.resources.allocation.MemoryAllocation;
import com.phonepe.drove.executor.engine.DockerExecutionEngine;
import com.phonepe.drove.models.application.PortSpec;
import com.phonepe.drove.models.application.executable.DockerCoordinates;
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
        e.startContainer(new InstanceSpec("T001",
                                          UUID.randomUUID().toString(),
                                          new DockerCoordinates("docker.io/santanusinha/test-service:0.1",
                                                                        Duration.seconds(100)),
                                          ImmutableList.of(new CPUAllocation(Collections.singletonMap(0, Collections.singleton(1))),
                                                                   new MemoryAllocation(Collections.singletonMap(0, 512L))),
                                          Collections.singletonList(new PortSpec("main", 3000)),
                                          Collections.emptyList(),
                                          null,
                                          null,
                                          Collections.emptyMap()),
                         null);
    }

}