package com.phonepe.drove.executor.managed;

import com.github.dockerjava.api.model.HostConfig;
import com.google.common.base.Strings;
import com.phonepe.drove.executor.AbstractExecutorEngineEnabledTestBase;
import com.phonepe.drove.executor.TestingUtils;
import com.phonepe.drove.executor.engine.DockerLabels;
import com.phonepe.drove.models.instance.InstanceInfo;
import com.phonepe.drove.models.instance.InstanceState;
import lombok.SneakyThrows;
import lombok.val;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 *
 */
class InstanceRecoveryTest extends AbstractExecutorEngineEnabledTestBase {

    @Test
    @SneakyThrows
    void testNoContainers() {
        val ir = new InstanceRecovery(engine, MAPPER, DOCKER_CLIENT);
        ir.start();
        assertEquals(0, engine.currentState().size());
        ir.stop();
    }

    @Test
    @SneakyThrows
    void testRecoverContainers() {
        val spec = TestingUtils.testSpec();

        var containerId = "";

        val instanceData = new InstanceInfo("TEST_APP-1",
                                            "TEST_APP",
                                            "INS1",
                                            "E1",
                                            null,
                                            null,
                                            InstanceState.HEALTHY,
                                            null,
                                            null,
                                            null);

        val instanceId = spec.getInstanceId();
        try {
            val createContainerResponse = DOCKER_CLIENT
                    .createContainerCmd(TestingUtils.IMAGE_NAME)
                    .withName("RecoveryTest")
                    .withLabels(Map.of(DockerLabels.DROVE_INSTANCE_ID_LABEL, instanceId,
                                       DockerLabels.DROVE_INSTANCE_SPEC_LABEL, MAPPER.writeValueAsString(spec),
                                       DockerLabels.DROVE_INSTANCE_DATA_LABEL, MAPPER.writeValueAsString(instanceData)))
                    .withHostConfig(new HostConfig()
                                            .withAutoRemove(true))
                    .exec();
            containerId = createContainerResponse.getId();
            DOCKER_CLIENT.startContainerCmd(containerId)
                    .exec();
            val ir = new InstanceRecovery(engine, MAPPER, DOCKER_CLIENT);
            ir.start();
            assertEquals(1, engine.currentState().size());
            ir.stop();
        }
        finally {
            if(!Strings.isNullOrEmpty(containerId)) {
                DOCKER_CLIENT.stopContainerCmd(containerId).exec();
            }
        }
    }

    @Test
    @SneakyThrows
    void testRecoverNonDroveContainers() {
        var containerId = "";
        try (val createCmd = DOCKER_CLIENT.createContainerCmd(UUID.randomUUID().toString())) {
            containerId = createCmd.withImage(TestingUtils.IMAGE_NAME)
                    .withName("test-container")
                    .withHostConfig(new HostConfig().withAutoRemove(true))
                    .exec()
                    .getId();
            DOCKER_CLIENT.startContainerCmd(containerId)
                    .exec();
            val ir = new InstanceRecovery(engine, MAPPER, DOCKER_CLIENT);
            ir.start();
            assertEquals(0, engine.currentState().size());
            ir.stop();
        }
        finally {
            if (!Strings.isNullOrEmpty(containerId)) {
                DOCKER_CLIENT.stopContainerCmd(containerId).exec();
            }
        }
    }

}