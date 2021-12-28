package com.phonepe.drove.executor.statemachine.actions;

import com.phonepe.drove.common.AbstractTestBase;
import com.phonepe.drove.common.StateData;
import com.phonepe.drove.executor.ExecutorTestingUtils;
import com.phonepe.drove.executor.statemachine.InstanceActionContext;
import com.phonepe.drove.models.instance.InstanceState;
import lombok.val;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 *
 */
class InstanceSpecValidatorTest extends AbstractTestBase {

    @Test
    void test() {
        val spec = ExecutorTestingUtils.testSpec();
        val action = new InstanceSpecValidator();
        val ctx = new InstanceActionContext(ExecutorTestingUtils.EXECUTOR_ID, spec, null);

        assertEquals(InstanceState.PROVISIONING,
                     action.execute(ctx,
                                    StateData.create(InstanceState.PENDING,
                                                     ExecutorTestingUtils.createExecutorInfo(spec, 8080))).getState());
    }
}