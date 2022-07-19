package com.phonepe.drove.executor.statemachine.application.actions;

import com.phonepe.drove.common.AbstractTestBase;
import com.phonepe.drove.common.CommonTestUtils;
import com.phonepe.drove.executor.ContainerHelperExtension;
import com.phonepe.drove.executor.logging.LogBus;
import com.phonepe.drove.executor.resourcemgmt.ResourceConfig;
import com.phonepe.drove.executor.statemachine.InstanceActionContext;
import com.phonepe.drove.statemachine.StateData;
import lombok.val;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static com.phonepe.drove.executor.ExecutorTestingUtils.*;
import static com.phonepe.drove.models.instance.InstanceState.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 *
 */
@ExtendWith(ContainerHelperExtension.class)
class ApplicationInstanceRunActionTest extends AbstractTestBase {

    @Test
    void testRun() {
        val spec = testSpec();
        val action = new ApplicationInstanceRunAction(new LogBus(), new ResourceConfig());
        val context = new InstanceActionContext(EXECUTOR_ID, spec, DOCKER_CLIENT);
        val resp = action.execute(context, StateData.create(STARTING, createExecutorInfo(spec, 8080)));
        assertEquals(UNREADY, resp.getState());
    }

    @Test
    void testRunFail() {
        val spec = testSpec(CommonTestUtils.IMAGE_NAME + "-invalid");
        val action = new ApplicationInstanceRunAction(new LogBus(), new ResourceConfig());
        val context = new InstanceActionContext(EXECUTOR_ID, spec, DOCKER_CLIENT);
        val resp = action.execute(context, StateData.create(STARTING, createExecutorInfo(spec, 8080)));
        assertEquals(START_FAILED, resp.getState());
    }

}