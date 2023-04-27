package com.phonepe.drove.executor.statemachine.application;

import com.google.inject.Guice;
import com.google.inject.Stage;
import com.phonepe.drove.common.AbstractTestBase;
import com.phonepe.drove.common.CommonTestUtils;
import com.phonepe.drove.executor.ExecutorTestingUtils;
import com.phonepe.drove.executor.InjectingApplicationInstanceActionFactory;
import com.phonepe.drove.executor.model.ExecutorInstanceInfo;
import com.phonepe.drove.models.instance.InstanceState;
import com.phonepe.drove.models.instance.LocalInstanceInfo;
import com.phonepe.drove.statemachine.StateData;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.concurrent.Executors;

import static com.phonepe.drove.models.instance.InstanceState.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 *
 */
@Slf4j
class ApplicationInstanceStateMachineTest extends AbstractTestBase {
    @Test
    void test() {
        val instanceSpec = ExecutorTestingUtils.testAppInstanceSpec();
        val sm = new ApplicationInstanceStateMachine(UUID.randomUUID().toString(),
                                                     instanceSpec,
                                                     StateData.create(InstanceState.PROVISIONING,
                                                                      new ExecutorInstanceInfo(instanceSpec.getAppId(),
                                                                                               instanceSpec.getAppName(),
                                                                                               instanceSpec.getInstanceId(),
                                                                                               instanceSpec.getAppId(),
                                                                                               new LocalInstanceInfo(
                                                                                                       "localhost",
                                                                                                       Map.of()),
                                                                                               List.of(),
                                                                                               Map.of(),
                                                                                               new Date(),
                                                                                               new Date())),
                                                     new InjectingApplicationInstanceActionFactory(Guice.createInjector(
                                                             Stage.DEVELOPMENT)),
                                                     ExecutorTestingUtils.DOCKER_CLIENT,
                                                     false);
        val stateChanges = new HashSet<>();
        sm.onStateChange().connect(sd -> stateChanges.add(sd.getState()));
        Executors.newSingleThreadExecutor()
                .submit(() -> {
                    try {
                        while (!sm.execute().isTerminal()) {

                        }
                        log.info("State machine execution completed");
                    }
                    catch (Exception e) {
                        log.error("Error running SM: ", e);
                    }
                });
        CommonTestUtils.waitUntil(() -> stateChanges.contains(HEALTHY));
        sm.stop();
        CommonTestUtils.waitUntil(() -> stateChanges.contains(STOPPED));
        assertEquals(EnumSet.of(STOPPED, STOPPING, STARTING, READY, DEPROVISIONING, HEALTHY, UNREADY), stateChanges);
    }

}