package com.phonepe.drove.executor.statemachine.actions;

import com.phonepe.drove.common.StateData;
import com.phonepe.drove.executor.AbstractExecutorTestBase;
import com.phonepe.drove.executor.ExecutorTestingUtils;
import com.phonepe.drove.executor.statemachine.InstanceActionContext;
import lombok.val;
import org.junit.jupiter.api.Test;

import static com.phonepe.drove.models.instance.InstanceState.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 *
 */
class ExecutableFetchActionTest extends AbstractExecutorTestBase {

    @Test
    void testFetchSuccess() {
        val ctx = new InstanceActionContext("EX1", ExecutorTestingUtils.testSpec(), DOCKER_CLIENT);
        val action = new ExecutableFetchAction();
        val response = action.execute(ctx,
                                      StateData.create(PENDING, ExecutorTestingUtils.createExecutorInfo(8080)));
        assertEquals(STARTING, response.getState());
    }

    @Test
    void testFetchWrongImage() {
        val ctx = new InstanceActionContext("EX1",
                                            ExecutorTestingUtils.testSpec(ExecutorTestingUtils.IMAGE_NAME + "-invalid"),
                                            DOCKER_CLIENT);
        val action = new ExecutableFetchAction();
        val response = action.execute(ctx,
                                      StateData.create(PENDING, ExecutorTestingUtils.createExecutorInfo(8080)));
        assertEquals(PROVISIONING_FAILED, response.getState());
    }

    @Test
    void testFetchInterrupt() {
        val ctx = new InstanceActionContext("EX1",
                                            ExecutorTestingUtils.testSpec(ExecutorTestingUtils.IMAGE_NAME + "-invalid"),
                                            DOCKER_CLIENT);
        val action = new ExecutableFetchAction();
        Thread.currentThread().interrupt();
        val response = action.execute(ctx,
                                      StateData.create(PENDING, ExecutorTestingUtils.createExecutorInfo(8080)));
        assertEquals(PROVISIONING_FAILED, response.getState());
    }
}