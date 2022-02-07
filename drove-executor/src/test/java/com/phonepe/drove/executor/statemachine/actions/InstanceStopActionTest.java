package com.phonepe.drove.executor.statemachine.actions;

import com.phonepe.drove.common.AbstractTestBase;
import com.phonepe.drove.common.StateData;
import com.phonepe.drove.executor.ContainerHelperExtension;
import com.phonepe.drove.executor.logging.LogBus;
import com.phonepe.drove.executor.resourcemgmt.ResourceConfig;
import com.phonepe.drove.executor.statemachine.InstanceActionContext;
import com.phonepe.drove.models.instance.InstanceState;
import lombok.val;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static com.phonepe.drove.executor.ExecutorTestingUtils.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 *
 */
@ExtendWith(ContainerHelperExtension.class)
class InstanceStopActionTest extends AbstractTestBase {

    @Test
    void testStopProper() {
        val spec = testSpec();
        val action = new InstanceStopAction();
        val context = new InstanceActionContext(EXECUTOR_ID, spec, DOCKER_CLIENT);
        val startAction = new InstanceRunAction(new LogBus(), new ResourceConfig());
        val state = startAction.execute(context,
                                        StateData.create(InstanceState.PROVISIONING, createExecutorInfo(spec, 8080)));
        assertEquals(InstanceState.DEPROVISIONING,
                     action.execute(context, StateData.from(state, InstanceState.HEALTHY)).getState());
    }

    @Test
    void testStopInvalidContainer() {
        val spec = testSpec();
        val action = new InstanceStopAction();
        val context = new InstanceActionContext(EXECUTOR_ID, spec, DOCKER_CLIENT);
        context.setDockerInstanceId("INVALID_CONTAINER_ID");
        assertEquals(InstanceState.DEPROVISIONING,
                     action.execute(context, StateData.create(InstanceState.HEALTHY, createExecutorInfo(spec, 8080)))
                             .getState());
    }
    @Test
    void testStopNoContainer() {
        val spec = testSpec();
        val action = new InstanceStopAction();
        val context = new InstanceActionContext(EXECUTOR_ID, spec, DOCKER_CLIENT);
        assertEquals(InstanceState.DEPROVISIONING,
                     action.execute(context, StateData.create(InstanceState.HEALTHY, createExecutorInfo(spec, 8080)))
                             .getState());
    }
}