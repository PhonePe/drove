package com.phonepe.drove.executor.statemachine.application.actions;

import com.phonepe.drove.common.AbstractTestBase;
import com.phonepe.drove.executor.ContainerHelperExtension;
import com.phonepe.drove.executor.resourcemgmt.ResourceConfig;
import com.phonepe.drove.executor.statemachine.InstanceActionContext;
import com.phonepe.drove.models.instance.InstanceState;
import com.phonepe.drove.statemachine.StateData;
import lombok.val;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static com.phonepe.drove.executor.ExecutorTestingUtils.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 *
 */
@ExtendWith(ContainerHelperExtension.class)
class ApplicationInstanceStopActionTest extends AbstractTestBase {

    @Test
    void testStopProper() {
        val spec = testAppInstanceSpec();
        val action = new ApplicationInstanceStopAction();
        val context = new InstanceActionContext<>(EXECUTOR_ID, spec, DOCKER_CLIENT);
        val startAction = new ApplicationInstanceRunAction(new ResourceConfig());
        val state = startAction.execute(context,
                                        StateData.create(InstanceState.PROVISIONING, createExecutorAppInstanceInfo(spec, 8080)));
        assertEquals(InstanceState.DEPROVISIONING,
                     action.execute(context, StateData.from(state, InstanceState.HEALTHY)).getState());
    }

    @Test
    void testStopInvalidContainer() {
        val spec = testAppInstanceSpec();
        val action = new ApplicationInstanceStopAction();
        val context = new InstanceActionContext<>(EXECUTOR_ID, spec, DOCKER_CLIENT);
        context.setDockerInstanceId("INVALID_CONTAINER_ID");
        assertEquals(InstanceState.DEPROVISIONING,
                     action.execute(context, StateData.create(InstanceState.HEALTHY, createExecutorAppInstanceInfo(spec, 8080)))
                             .getState());
    }
    @Test
    void testStopNoContainer() {
        val spec = testAppInstanceSpec();
        val action = new ApplicationInstanceStopAction();
        val context = new InstanceActionContext<>(EXECUTOR_ID, spec, DOCKER_CLIENT);
        assertEquals(InstanceState.DEPROVISIONING,
                     action.execute(context, StateData.create(InstanceState.HEALTHY, createExecutorAppInstanceInfo(spec, 8080)))
                             .getState());
    }
}