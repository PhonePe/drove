package com.phonepe.drove.executor.statemachine;

import com.google.common.collect.ImmutableList;
import com.phonepe.drove.common.StateData;
import com.phonepe.drove.internalmodels.InstanceSpec;
import com.phonepe.drove.models.application.AppId;
import com.phonepe.drove.models.application.PortSpec;
import com.phonepe.drove.models.application.checks.CheckSpec;
import com.phonepe.drove.models.application.checks.HTTPCheckModeSpec;
import com.phonepe.drove.models.application.executable.DockerCoordinates;
import com.phonepe.drove.models.application.requirements.CPURequirement;
import com.phonepe.drove.models.application.requirements.MemoryRequirement;
import com.phonepe.drove.models.common.HTTPVerb;
import com.phonepe.drove.models.instance.InstanceState;
import io.dropwizard.util.Duration;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.Date;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 *
 */
@Slf4j
class InstanceStateMachineTest {
    @Test
    void test() {
        val instanceSpec = new InstanceSpec(new AppId("test", 1),
                                            new DockerCoordinates(
                                                    "docker.io/santanusinha/test-service:0.1",
                                                    Duration.seconds(100)),
                                            ImmutableList.of(new CPURequirement(1),
                                                             new MemoryRequirement(512)),
                                            Collections.singletonList(new PortSpec("main", 3000)),
                                            Collections.emptyList(),
                                            new CheckSpec(new HTTPCheckModeSpec("http",
                                                                                "main",
                                                                                "/",
                                                                                HTTPVerb.GET,
                                                                                Collections.singleton(200),
                                                                                "",
                                                                                Duration.seconds(1)),
                                                          Duration.seconds(1),
                                                          Duration.seconds(3),
                                                          3,
                                                          Duration.seconds(0)),
                                            new CheckSpec(new HTTPCheckModeSpec("http",
                                                                                "main",
                                                                                "/",
                                                                                HTTPVerb.GET,
                                                                                Collections.singleton(200),
                                                                                "",
                                                                                Duration.seconds(1)),
                                                          Duration.seconds(1),
                                                          Duration.seconds(3),
                                                          3,
                                                          Duration.seconds(0)),
                                            Collections.emptyMap());
        val sm = new InstanceStateMachine(instanceSpec, StateData.create(InstanceState.PROVISIONING, null));
        sm.onStateChange().connect(sd -> log.info("Current state: {}", sd));
        val done = new AtomicBoolean();
        Executors.newSingleThreadExecutor()
                .submit(() -> {
                    try {
                        while (!sm.execute().isTerminal()) {}
                        done.set(true);
                        log.info("State machine execution completed");
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                });
        val end = new Date(new Date().getTime() + 70_000);
        Awaitility.await()
                .pollDelay(java.time.Duration.ofSeconds(1))
                .timeout(130, TimeUnit.SECONDS)
                .until(() -> new Date().after(end) || done.get());
        if(!done.get()) {
            sm.stop();
            log.debug("Stop called on sm");
            Awaitility.await()
                    .pollDelay(java.time.Duration.ofSeconds(1))
                    .forever()
                    .until(done::get);
        }
    }

}