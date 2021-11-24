package com.phonepe.drove.executor;

import com.google.inject.Guice;
import com.google.inject.Stage;
import com.phonepe.drove.common.CommonTestUtils;
import com.phonepe.drove.common.model.MessageDeliveryStatus;
import com.phonepe.drove.common.model.MessageHeader;
import com.phonepe.drove.common.model.MessageResponse;
import com.phonepe.drove.common.model.executor.StartInstanceMessage;
import com.phonepe.drove.common.model.executor.StopInstanceMessage;
import com.phonepe.drove.executor.engine.ExecutorCommunicator;
import com.phonepe.drove.executor.engine.InstanceEngine;
import com.phonepe.drove.executor.managed.ExecutorIdManager;
import com.phonepe.drove.executor.resourcemgmt.ResourceDB;
import com.phonepe.drove.models.instance.InstanceState;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 *
 */
@Slf4j
class ExecutorCommunicatorTest {
    @Test
    void test() {
        val engine = new InstanceEngine(new ExecutorIdManager(3000), Executors.newCachedThreadPool(),
                                        new InjectingInstanceActionFactory(Guice.createInjector(Stage.DEVELOPMENT)),
                                        new ResourceDB());
        val comms = new ExecutorCommunicator(
                engine, msg -> {
            log.info("Received message: {}", msg);
            return new MessageResponse(MessageHeader.controllerResponse(msg.getHeader().getId()),
                                       MessageDeliveryStatus.ACCEPTED);
        });
        comms.onResponse().connect(response -> log.info("Response: {}", response));
        val runningCount = new AtomicInteger();
        engine.onStateChange().connect(newState -> {
            if (newState.getState().equals(InstanceState.PROVISIONING)) {
                runningCount.getAndIncrement();
            }
            if (newState.getState().isTerminal()) {
                runningCount.decrementAndGet();
            }
        });
        val spec = TestingUtils.testSpec();
        val address = TestingUtils.localAddress();
        comms.receive(new StartInstanceMessage(MessageHeader.controllerRequest(), address, spec));
        CommonTestUtils.delay(Duration.ofSeconds(70));
        comms.receive(new StopInstanceMessage(MessageHeader.controllerRequest(), address, spec.getInstanceId()));
        Awaitility.await()
                .forever()
                .pollDelay(2, TimeUnit.SECONDS)
                .pollInterval(1, TimeUnit.SECONDS)
                .until(() -> runningCount.get() == 0);
    }

}