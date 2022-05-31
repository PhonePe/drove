package com.phonepe.drove.executor.statemachine.actions;

import com.phonepe.drove.executor.ExecutorTestingUtils;
import com.phonepe.drove.executor.statemachine.InstanceActionContext;
import com.phonepe.drove.models.instance.InstanceState;
import com.phonepe.drove.statemachine.StateData;
import lombok.val;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 *
 */
class InstanceDestroyActionTest {

    @Test
    void test() {
        val spec = ExecutorTestingUtils.testSpec("hello-world");
        val ctx = new InstanceActionContext(ExecutorTestingUtils.EXECUTOR_ID, spec, null);
        val action = new InstanceDestroyAction();
        val response = action.execute(ctx,
                                      StateData.create(InstanceState.HEALTHY,
                                                       ExecutorTestingUtils.createExecutorInfo(spec, 8080)));
        assertEquals(InstanceState.DEPROVISIONING, response.getState());
    }
}