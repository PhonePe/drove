package com.phonepe.drove.executor;

import com.google.common.collect.ImmutableList;
import com.phonepe.drove.common.CommonUtils;
import com.phonepe.drove.common.StateData;
import com.phonepe.drove.common.model.InstanceSpec;
import com.phonepe.drove.executor.logging.LogBus;
import com.phonepe.drove.executor.model.ExecutorInstanceInfo;
import com.phonepe.drove.executor.statemachine.InstanceActionContext;
import com.phonepe.drove.executor.statemachine.actions.ExecutableFetchAction;
import com.phonepe.drove.executor.statemachine.actions.InstanceRunAction;
import com.phonepe.drove.models.application.PortSpec;
import com.phonepe.drove.models.application.PortType;
import com.phonepe.drove.models.application.executable.DockerCoordinates;
import com.phonepe.drove.models.application.logging.LocalLoggingSpec;
import com.phonepe.drove.models.info.resources.allocation.CPUAllocation;
import com.phonepe.drove.models.info.resources.allocation.MemoryAllocation;
import com.phonepe.drove.models.instance.InstanceState;
import com.phonepe.drove.models.instance.LocalInstanceInfo;
import io.dropwizard.util.Duration;
import lombok.val;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.Date;
import java.util.UUID;

import static com.phonepe.drove.models.instance.InstanceState.PROVISIONING;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 *
 */
class InstanceRunActionTest extends AbstractExecutorBaseTest {

    @Test
    void testRun() {
        val appId = "T001";
        val instanceId = UUID.randomUUID().toString();
        val instanceSpec = new InstanceSpec(appId,
                                            "TEST_APP",
                                            instanceId,
                                            new DockerCoordinates(
                                                    "docker.io/santanusinha/test-service:0.1",
                                                    Duration.seconds(100)),
                                            ImmutableList.of(new CPUAllocation(Collections.singletonMap(0,
                                                                                                        Collections.singleton(
                                                                                                                1))),
                                                             new MemoryAllocation(Collections.singletonMap(0, 512L))),
                                            Collections.singletonList(new PortSpec("main", 3000, PortType.HTTP)),
                                            Collections.emptyList(),
                                            null,
                                            null,
                                            LocalLoggingSpec.DEFAULT,
                                            Collections.emptyMap());
        val executorId = CommonUtils.executorId(3000);
        val ctx = new InstanceActionContext(executorId, instanceSpec, DOCKER_CLIENT);
        new ExecutableFetchAction().execute(ctx, StateData.create(InstanceState.PENDING, null));
        val newState
                = new InstanceRunAction(new LogBus()).execute(ctx,
                                                            StateData.create(PROVISIONING,
                                                                   new ExecutorInstanceInfo(instanceSpec.getAppId(),
                                                                                            instanceSpec.getAppName(),
                                                                                            instanceSpec.getInstanceId(),
                                                                                            executorId,
                                                                                            new LocalInstanceInfo(CommonUtils.hostname(),
                                                                                                          Collections.emptyMap()),
                                                                                            instanceSpec.getResources(),
                                                                                            Collections.emptyMap(),
                                                                                            new Date(),
                                                                                            new Date()),
                                                                   ""));
        assertEquals(InstanceState.UNREADY, newState.getState());
    }
}