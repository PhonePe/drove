package com.phonepe.drove.executor.managed;

import com.github.dockerjava.api.model.HostConfig;
import com.google.common.base.Strings;
import com.phonepe.drove.executor.AbstractExecutorEngineEnabledTestBase;
import com.phonepe.drove.executor.TestingUtils;
import com.phonepe.drove.models.instance.InstanceInfo;
import lombok.SneakyThrows;
import lombok.val;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

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
        val instanceId = TestingUtils.executeOnceContainerStarted(engine, new Function<InstanceInfo, String>() {
            @Override
            @SneakyThrows
            public String apply(InstanceInfo info) {
                val ir = new InstanceRecovery(engine, MAPPER, DOCKER_CLIENT);
                ir.start();
                assertEquals(1, engine.currentState().size());
                ir.stop();
                return info.getInstanceId();
            }
        });
        assertNotNull(instanceId);
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
            if(!Strings.isNullOrEmpty(containerId)) {
                DOCKER_CLIENT.stopContainerCmd(containerId).exec();
            }
        }
    }

}