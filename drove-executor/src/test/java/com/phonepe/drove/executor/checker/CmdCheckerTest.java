package com.phonepe.drove.executor.checker;

import com.github.dockerjava.api.exception.NotFoundException;
import com.github.dockerjava.api.model.HostConfig;
import com.google.common.base.Strings;
import com.phonepe.drove.common.AbstractTestBase;
import com.phonepe.drove.common.model.ApplicationInstanceSpec;
import com.phonepe.drove.executor.statemachine.InstanceActionContext;
import com.phonepe.drove.executor.utils.ImagePullProgressHandler;
import com.phonepe.drove.models.application.CheckResult;
import com.phonepe.drove.models.application.checks.CheckMode;
import com.phonepe.drove.models.application.checks.CmdCheckModeSpec;
import lombok.SneakyThrows;
import lombok.val;
import org.junit.jupiter.api.Test;

import static com.phonepe.drove.common.CommonTestUtils.TASK_IMAGE_NAME;
import static com.phonepe.drove.executor.ExecutorTestingUtils.DOCKER_CLIENT;
import static org.junit.jupiter.api.Assertions.*;

/**
 *
 */
class CmdCheckerTest extends AbstractTestBase {

    @Test
    void testCmdSuccess() {
        runTest(containerId -> {
            val ctx = new InstanceActionContext<ApplicationInstanceSpec>("EX1", null, DOCKER_CLIENT)
                    .setDockerInstanceId(containerId);
            try (val cc = new CmdChecker(new CmdCheckModeSpec("/bin/echo XX"), ctx)) {
                val result = cc.call();
                assertEquals(CheckResult.Status.HEALTHY, result.getStatus());
                assertTrue(Strings.isNullOrEmpty(result.getMessage()));
                assertEquals(CheckMode.CMD, cc.mode());
            }
        });
    }

    @Test
    void testCmdFail() {
        runTest(containerId -> {
            val ctx = new InstanceActionContext<ApplicationInstanceSpec>("EX1", null, DOCKER_CLIENT)
                    .setDockerInstanceId(containerId);
            try(val cc = new CmdChecker(new CmdCheckModeSpec("blah"), ctx)) {
                val result = cc.call();
                assertEquals(CheckResult.Status.UNHEALTHY, result.getStatus());
                assertFalse(Strings.isNullOrEmpty(result.getMessage()));
            }
        });
    }

    @Test
    @SneakyThrows
    void testNoContainer() {
        val ctx = new InstanceActionContext<ApplicationInstanceSpec>("EX1", null, DOCKER_CLIENT);
        try(val cc = new CmdChecker(new CmdCheckModeSpec("blah"), ctx)) {
            val result = cc.call();
            assertEquals(CheckResult.Status.UNHEALTHY, result.getStatus());
            assertFalse(Strings.isNullOrEmpty(result.getMessage()));
        }
    }

    @Test
    @SneakyThrows
    void testError() {
        val ctx = new InstanceActionContext<ApplicationInstanceSpec>("EX1", null, DOCKER_CLIENT)
                .setDockerInstanceId("WrongId");
        try(val cc = new CmdChecker(new CmdCheckModeSpec("blah"), ctx)) {
            assertEquals(CheckResult.Status.UNHEALTHY, cc.call().getStatus());
        }
        catch (NotFoundException e) {
            return;
        }
        fail("Should have thrown NotFoundException");
    }

    @FunctionalInterface
    private interface TestConsumer<T> {
        void accept(T t) throws Throwable;
    }

    @SneakyThrows
    void runTest(TestConsumer<String> test) {
        DOCKER_CLIENT.pullImageCmd(TASK_IMAGE_NAME)
                .exec(new ImagePullProgressHandler(TASK_IMAGE_NAME))
                .awaitCompletion();
        val containerId = DOCKER_CLIENT.createContainerCmd(TASK_IMAGE_NAME)
                .withHostConfig(new HostConfig().withAutoRemove(true))
                .withAttachStderr(true)
                .withAttachStdout(true)
                .withEnv("ITERATIONS=1000")
                .exec()
                .getId();
        DOCKER_CLIENT.startContainerCmd(containerId).exec();
        try {
            test.accept(containerId);
        }
        finally {
            DOCKER_CLIENT.stopContainerCmd(containerId).exec();
        }
    }

}