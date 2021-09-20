package com.phonepe.drove.executor;

import com.google.inject.Guice;
import com.google.inject.Stage;
import com.phonepe.drove.common.CommonTestUtils;
import com.phonepe.drove.common.model.executor.StartInstanceMessage;
import com.phonepe.drove.common.model.executor.StopInstanceMessage;
import com.phonepe.drove.executor.engine.ExecutorCommunicator;
import com.phonepe.drove.executor.engine.InstanceEngine;
import com.phonepe.drove.common.model.MessageDeliveryStatus;
import com.phonepe.drove.common.model.MessageHeader;
import com.phonepe.drove.common.model.MessageResponse;
import com.phonepe.drove.executor.resource.ResourceDB;
import com.phonepe.drove.models.instance.InstanceState;
import io.dropwizard.util.Duration;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;

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
        val engine = new InstanceEngine(Executors.newCachedThreadPool(),
                                        new InjectingInstanceActionFactory(Guice.createInjector(Stage.DEVELOPMENT)),
                                        new ResourceDB());
        val comms = new ExecutorCommunicator(engine);
        comms.onMessageReady().connect(msg -> {
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
        comms.receive(new StartInstanceMessage(MessageHeader.controllerRequest(), spec));
        CommonTestUtils.delay(Duration.seconds(70));
        comms.receive(new StopInstanceMessage(MessageHeader.controllerRequest(), spec.getInstanceId()));
        Awaitility.await()
                .forever()
                .pollDelay(2, TimeUnit.SECONDS)
                .pollInterval(1, TimeUnit.SECONDS)
                .until(() -> runningCount.get() == 0);
    }

}