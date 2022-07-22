package com.phonepe.drove.executor.statemachine.application.actions;

import com.phonepe.drove.common.AbstractTestBase;
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
class ApplicationApplicationInstanceSpecValidatorTest extends AbstractTestBase {

    @Test
    void test() {
        val spec = ExecutorTestingUtils.testAppInstanceSpec();
        val action = new ApplicationInstanceSpecValidator();
        val ctx = new InstanceActionContext<>(ExecutorTestingUtils.EXECUTOR_ID, spec, null);

        assertEquals(InstanceState.PROVISIONING,
                     action.execute(ctx,
                                    StateData.create(InstanceState.PENDING,
                                                     ExecutorTestingUtils.createExecutorInfo(spec, 8080))).getState());
    }
}