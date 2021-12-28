package com.phonepe.drove.executor.statemachine.actions;

import com.phonepe.drove.common.AbstractTestBase;
import com.phonepe.drove.common.StateData;
import com.phonepe.drove.executor.ContainerHelperExtension;
import com.phonepe.drove.executor.ExecutorTestingUtils;
import com.phonepe.drove.executor.logging.LogBus;
import com.phonepe.drove.executor.statemachine.InstanceActionContext;
import com.phonepe.drove.models.instance.InstanceState;
import lombok.SneakyThrows;
import lombok.val;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 *
 */
@ExtendWith(ContainerHelperExtension.class)
class InstanceRecoveryActionTest extends AbstractTestBase {

    @Test
    @SneakyThrows
    void testRecovery() {
        val spec = ExecutorTestingUtils.testSpec();

        var containerId = "";

        val instanceData = ExecutorTestingUtils.createExecutorInfo(spec, 8080);

        val instanceId = spec.getInstanceId();
        ExecutorTestingUtils.startTestContainer(spec, instanceData, AbstractTestBase.MAPPER);
        val ir = new InstanceRecoveryAction(new LogBus());
        val ctx = new InstanceActionContext("E1", spec, ExecutorTestingUtils.DOCKER_CLIENT);
        val r = ir.execute(ctx, StateData.create(InstanceState.UNKNOWN, instanceData));
        assertEquals(InstanceState.UNREADY, r.getState());
        ir.stop();
    }

    @Test
    @SneakyThrows
    void testRecoverNone() {
        val spec = ExecutorTestingUtils.testSpec();
        val ir = new InstanceRecoveryAction(new LogBus());
        val ctx = new InstanceActionContext("E1", spec, ExecutorTestingUtils.DOCKER_CLIENT);
        val r = ir.execute(ctx,
                           StateData.create(InstanceState.UNKNOWN,
                                            ExecutorTestingUtils.createExecutorInfo(spec, 8080)));
        assertEquals(InstanceState.STOPPED, r.getState());
    }

}