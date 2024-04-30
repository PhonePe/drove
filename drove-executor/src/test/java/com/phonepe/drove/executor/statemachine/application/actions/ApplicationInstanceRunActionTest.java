package com.phonepe.drove.executor.statemachine.application.actions;

import com.phonepe.drove.common.AbstractTestBase;
import com.phonepe.drove.common.CommonTestUtils;
import com.phonepe.drove.executor.ContainerHelperExtension;
import com.phonepe.drove.executor.ExecutorOptions;
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
        val spec = testAppInstanceSpec();
        val action = new ApplicationInstanceRunAction(new ResourceConfig(),
                                                      ExecutorOptions.DEFAULT,
                                                      CommonTestUtils.httpCaller(),
                                                      MAPPER);
        val context = new InstanceActionContext<>(EXECUTOR_ID, spec, DOCKER_CLIENT, false);
        val resp = action.execute(context, StateData.create(STARTING, createExecutorAppInstanceInfo(spec, 8080)));
        assertEquals(UNREADY, resp.getState());
    }

    @Test
    void testRunFail() {
        val spec = testAppInstanceSpec(CommonTestUtils.APP_IMAGE_NAME + "-invalid");
        val action = new ApplicationInstanceRunAction(new ResourceConfig(),
                                                      ExecutorOptions.DEFAULT,
                                                      CommonTestUtils.httpCaller(),
                                                      MAPPER);
        val context = new InstanceActionContext<>(EXECUTOR_ID, spec, DOCKER_CLIENT, false);
        val resp = action.execute(context, StateData.create(STARTING, createExecutorAppInstanceInfo(spec, 8080)));
        assertEquals(START_FAILED, resp.getState());
    }

}