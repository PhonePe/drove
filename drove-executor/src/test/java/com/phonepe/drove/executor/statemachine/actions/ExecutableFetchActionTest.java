package com.phonepe.drove.executor.statemachine.actions;

import com.github.dockerjava.api.model.Image;
import com.google.common.base.Strings;
import com.phonepe.drove.common.AbstractTestBase;
import com.phonepe.drove.common.CommonTestUtils;
import com.phonepe.drove.common.StateData;
import com.phonepe.drove.executor.ExecutorTestingUtils;
import com.phonepe.drove.executor.statemachine.InstanceActionContext;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.junit.jupiter.api.Test;

import static com.phonepe.drove.models.instance.InstanceState.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 *
 */
@Slf4j
class ExecutableFetchActionTest extends AbstractTestBase {

    @Test
    void testFetchSuccess() {
        val spec = ExecutorTestingUtils.testSpec("hello-world");
        val ctx = new InstanceActionContext(ExecutorTestingUtils.EXECUTOR_ID, spec, ExecutorTestingUtils.DOCKER_CLIENT);
        val action = new ExecutableFetchAction();
        val response = action.execute(ctx,
                                      StateData.create(PENDING, ExecutorTestingUtils.createExecutorInfo(spec, 8080)));
        try {
            assertEquals(STARTING, response.getState());
        }
        finally {
            val imageId = ExecutorTestingUtils.DOCKER_CLIENT.listImagesCmd()
                    .withImageNameFilter("hello-world")
                    .exec()
                    .stream()
                    .findAny()
                    .map(Image::getId)
                    .orElse(null);
            log.info("Hello world image id: {}", imageId);
            if(!Strings.isNullOrEmpty(imageId)) {
                ExecutorTestingUtils.DOCKER_CLIENT.removeImageCmd(imageId).exec();
            }
        }
    }

    @Test
    void testFetchWrongImage() {
        val spec = ExecutorTestingUtils.testSpec(CommonTestUtils.IMAGE_NAME + "-invalid");
        val ctx = new InstanceActionContext(ExecutorTestingUtils.EXECUTOR_ID,
                                            spec,
                                            ExecutorTestingUtils.DOCKER_CLIENT);
        val action = new ExecutableFetchAction();
        val response = action.execute(ctx,
                                      StateData.create(PENDING, ExecutorTestingUtils.createExecutorInfo(spec,8080)));
        assertEquals(PROVISIONING_FAILED, response.getState());
    }

    @Test
    void testFetchInterrupt() {
        val spec = ExecutorTestingUtils.testSpec(CommonTestUtils.IMAGE_NAME + "-invalid");
        val ctx = new InstanceActionContext(ExecutorTestingUtils.EXECUTOR_ID,
                                            spec,
                                            ExecutorTestingUtils.DOCKER_CLIENT);
        val action = new ExecutableFetchAction();
        Thread.currentThread().interrupt();
        val response = action.execute(ctx,
                                      StateData.create(PENDING, ExecutorTestingUtils.createExecutorInfo(spec,8080)));
        assertEquals(PROVISIONING_FAILED, response.getState());
    }
}