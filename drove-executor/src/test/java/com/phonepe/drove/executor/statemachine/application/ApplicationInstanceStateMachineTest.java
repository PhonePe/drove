package com.phonepe.drove.executor.statemachine.application;

import com.google.inject.Guice;
import com.google.inject.Stage;
import com.phonepe.drove.common.AbstractTestBase;
import com.phonepe.drove.common.CommonTestUtils;
import com.phonepe.drove.executor.ExecutorTestingUtils;
import com.phonepe.drove.executor.InjectingApplicationInstanceActionFactory;
import com.phonepe.drove.models.instance.InstanceState;
import com.phonepe.drove.statemachine.StateData;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.phonepe.drove.common.CommonTestUtils.waitUntil;

/**
 *
 */
@Slf4j
@Disabled
class ApplicationInstanceStateMachineTest extends AbstractTestBase {
    @Test
    void test() {
        val instanceSpec = ExecutorTestingUtils.testAppInstanceSpec();
        val sm = new ApplicationInstanceStateMachine(UUID.randomUUID().toString(),
                                                     instanceSpec,
                                                     StateData.create(InstanceState.PROVISIONING, null),
                                                     new InjectingApplicationInstanceActionFactory(Guice.createInjector(
                                                  Stage.DEVELOPMENT)),
                                                     ExecutorTestingUtils.DOCKER_CLIENT);
        sm.onStateChange().connect(sd -> log.info("Current state: {}", sd));
        val done = new AtomicBoolean();
        Executors.newSingleThreadExecutor()
                .submit(() -> {
                    try {
                        while (!sm.execute().isTerminal()) {

                        }
                        done.set(true);
                        log.info("State machine execution completed");
                    }
                    catch (Exception e) {
                        e.printStackTrace();
                    }
                });
        CommonTestUtils.delay(Duration.ofSeconds(120));
        if (!done.get()) {
            sm.stop();
            log.debug("Stop called on sm");
            waitUntil(done::get);
        }
    }

    @Test
    void testRecovery() {

    }

}