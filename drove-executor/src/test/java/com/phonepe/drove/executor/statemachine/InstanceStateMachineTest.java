package com.phonepe.drove.executor.statemachine;

import com.google.inject.Guice;
import com.google.inject.Stage;
import com.phonepe.drove.common.CommonTestUtils;
import com.phonepe.drove.common.StateData;
import com.phonepe.drove.executor.AbstractExecutorBaseTest;
import com.phonepe.drove.executor.InjectingInstanceActionFactory;
import com.phonepe.drove.executor.TestingUtils;
import com.phonepe.drove.models.instance.InstanceState;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 *
 */
@Slf4j
@Disabled
class InstanceStateMachineTest extends AbstractExecutorBaseTest {
    @Test
    void test() {
        val instanceSpec = TestingUtils.testSpec();
        val sm = new InstanceStateMachine(UUID.randomUUID().toString(),
                                          instanceSpec,
                                          StateData.create(InstanceState.PROVISIONING, null),
                                          new InjectingInstanceActionFactory(Guice.createInjector(
                                                  Stage.DEVELOPMENT)),
                                          DOCKER_CLIENT);
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
            Awaitility.await()
                    .pollDelay(java.time.Duration.ofSeconds(1))
                    .forever()
                    .until(done::get);
        }
    }

    @Test
    void testRecovery() {

    }

}