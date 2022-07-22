package com.phonepe.drove.executor.managed;

import com.github.dockerjava.api.model.HostConfig;
import com.google.common.base.Strings;
import com.phonepe.drove.common.AbstractTestBase;
import com.phonepe.drove.common.CommonTestUtils;
import com.phonepe.drove.executor.AbstractExecutorEngineEnabledTestBase;
import com.phonepe.drove.executor.ContainerHelperExtension;
import com.phonepe.drove.executor.ExecutorTestingUtils;
import lombok.SneakyThrows;
import lombok.val;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 *
 */
@ExtendWith(ContainerHelperExtension.class)
class InstanceRecoveryTest extends AbstractExecutorEngineEnabledTestBase {

    @Test
    @SneakyThrows
    void testNoContainers() {
        val ir = new InstanceRecovery(applicationInstanceEngine, AbstractTestBase.MAPPER, ExecutorTestingUtils.DOCKER_CLIENT);
        ir.start();
        assertEquals(0, applicationInstanceEngine.currentState().size());
        ir.stop();
    }

    @Test
    @SneakyThrows
    void testRecoverContainers() {
        val spec = ExecutorTestingUtils.testAppInstanceSpec();
        val instanceData = ExecutorTestingUtils.createExecutorInfo(spec, 8080);
        ExecutorTestingUtils.startTestContainer(spec, instanceData, AbstractTestBase.MAPPER);
        val ir = new InstanceRecovery(applicationInstanceEngine, AbstractTestBase.MAPPER, ExecutorTestingUtils.DOCKER_CLIENT);
        ir.start();
        assertEquals(1, applicationInstanceEngine.currentState().size());
        ir.stop();
    }

    @Test
    @SneakyThrows
    void testRecoverNonDroveContainers() {
        var containerId = "";
        try (val createCmd = ExecutorTestingUtils.DOCKER_CLIENT.createContainerCmd(CommonTestUtils.APP_IMAGE_NAME)) {
            containerId = createCmd.withName("test-container")
                    .withHostConfig(new HostConfig().withAutoRemove(true))
                    .exec()
                    .getId();
            ExecutorTestingUtils.DOCKER_CLIENT.startContainerCmd(containerId)
                    .exec();
            val ir = new InstanceRecovery(applicationInstanceEngine, AbstractTestBase.MAPPER, ExecutorTestingUtils.DOCKER_CLIENT);
            ir.start();
            assertEquals(0, applicationInstanceEngine.currentState().size());
            ir.stop();
        }
        finally {
            if (!Strings.isNullOrEmpty(containerId)) {
                ExecutorTestingUtils.DOCKER_CLIENT.stopContainerCmd(containerId).exec();
            }
        }
    }

}