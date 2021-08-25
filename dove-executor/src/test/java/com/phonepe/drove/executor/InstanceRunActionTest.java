package com.phonepe.drove.executor;

import com.google.common.collect.ImmutableList;
import com.phonepe.drove.common.StateData;
import com.phonepe.drove.executor.statemachine.actions.ExecutableFetchAction;
import com.phonepe.drove.executor.statemachine.actions.InstanceRunAction;
import com.phonepe.drove.executor.statemachine.InstanceActionContext;
import com.phonepe.drove.internalmodels.InstanceSpec;
import com.phonepe.drove.models.application.AppId;
import com.phonepe.drove.models.application.PortSpec;
import com.phonepe.drove.models.application.executable.DockerCoordinates;
import com.phonepe.drove.models.application.requirements.CPURequirement;
import com.phonepe.drove.models.application.requirements.MemoryRequirement;
import com.phonepe.drove.models.instance.InstanceInfo;
import com.phonepe.drove.models.instance.InstanceState;
import io.dropwizard.util.Duration;
import lombok.val;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.Date;

import static com.phonepe.drove.models.instance.InstanceState.PROVISIONING;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 *
 */
class InstanceRunActionTest {

    @Test
    void testRun() {
        val instanceSpec = new InstanceSpec(new AppId("test", 1),
                                            new DockerCoordinates(
                                                    "docker.io/santanusinha/test-service:0.1",
                                                    Duration.seconds(100)),
                                            ImmutableList.of(new CPURequirement(1),
                                                             new MemoryRequirement(512)),
                                            Collections.singletonList(new PortSpec("main", 3000)),
                                            Collections.emptyList(),
                                            null,
                                            null,
                                            Collections.emptyMap());
        val ctx = new InstanceActionContext(instanceSpec);
        new ExecutableFetchAction().execute(ctx, StateData.create(InstanceState.PENDING, null));
        val newState = new InstanceRunAction().execute(ctx,
                                                       StateData.create(PROVISIONING,
                                                      new InstanceInfo(instanceSpec.getAppId(),
                                                                       null,
                                                                       "",
                                                                       PROVISIONING,
                                                                       Collections.emptyMap(),
                                                                       Collections.emptyMap(),
                                                                       new Date(),
                                                                       new Date()),
                                                      ""));
        assertEquals(InstanceState.UNREADY, newState.getState());
    }
}