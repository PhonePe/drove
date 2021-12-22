package com.phonepe.drove.executor.statemachine.actions;

import com.phonepe.drove.common.StateData;
import com.phonepe.drove.executor.ExecutorTestingUtils;
import com.phonepe.drove.executor.statemachine.InstanceActionContext;
import com.phonepe.drove.models.instance.InstanceState;
import lombok.val;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 *
 */
class InstanceDestroyActionTest {

    @Test
    void test() {
        val ctx = new InstanceActionContext("EX1", ExecutorTestingUtils.testSpec("hello-world"), null);
        val action = new InstanceDestroyAction();
        val response = action.execute(ctx,
                                      StateData.create(InstanceState.HEALTHY,
                                                       ExecutorTestingUtils.createExecutorInfo(8080)));
        assertEquals(InstanceState.DEPROVISIONING, response.getState());
    }
}