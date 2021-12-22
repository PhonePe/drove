package com.phonepe.drove.executor.engine;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Sets;
import com.phonepe.drove.common.model.InstanceSpec;
import com.phonepe.drove.common.model.MessageDeliveryStatus;
import com.phonepe.drove.common.model.MessageHeader;
import com.phonepe.drove.common.model.executor.ExecutorAddress;
import com.phonepe.drove.common.model.executor.StartInstanceMessage;
import com.phonepe.drove.common.model.executor.StopInstanceMessage;
import com.phonepe.drove.executor.AbstractExecutorEngineEnabledTestBase;
import com.phonepe.drove.executor.ExecutorTestingUtils;
import com.phonepe.drove.models.application.PortSpec;
import com.phonepe.drove.models.application.PortType;
import com.phonepe.drove.models.application.checks.CheckSpec;
import com.phonepe.drove.models.application.checks.HTTPCheckModeSpec;
import com.phonepe.drove.models.application.executable.DockerCoordinates;
import com.phonepe.drove.models.application.logging.LocalLoggingSpec;
import com.phonepe.drove.models.common.HTTPVerb;
import com.phonepe.drove.models.info.resources.allocation.CPUAllocation;
import com.phonepe.drove.models.info.resources.allocation.MemoryAllocation;
import com.phonepe.drove.models.instance.InstanceInfo;
import com.phonepe.drove.models.instance.InstanceState;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.junit.jupiter.api.Test;

import java.util.*;

import static com.phonepe.drove.common.CommonTestUtils.waitUntil;
import static com.phonepe.drove.models.instance.InstanceState.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 *
 */
@Slf4j
class InstanceEngineTest extends AbstractExecutorEngineEnabledTestBase {

    @Test
    void testBasicRun() {
        val spec = ExecutorTestingUtils.testSpec();
        val instanceId = spec.getInstanceId();
        val stateChanges = new HashSet<InstanceState>();
        engine.onStateChange().connect(state -> {
            if (state.getInstanceId().equals(instanceId)) {
                stateChanges.add(state.getState());
            }
        });
        val executorAddress = new ExecutorAddress("eid", "localhost", 3000);
        val startInstanceMessage = new StartInstanceMessage(MessageHeader.controllerRequest(),
                                                            executorAddress,
                                                            spec);
        val startResponse = engine.handleMessage(startInstanceMessage);
        assertEquals(MessageDeliveryStatus.ACCEPTED, startResponse.getStatus());
        assertEquals(MessageDeliveryStatus.FAILED, engine.handleMessage(startInstanceMessage).getStatus());
        waitUntil(() -> engine.currentState(instanceId)
                        .map(InstanceInfo::getState)
                        .map(instanceState -> instanceState.equals(HEALTHY))
                        .orElse(false));
        assertTrue(engine.exists(instanceId));
        assertFalse(engine.exists("WrongId"));
        val info = engine.currentState(instanceId).orElse(null);
        assertNotNull(info);
        assertEquals(HEALTHY, info.getState());
        val allInfo = engine.currentState();
        assertEquals(1, allInfo.size());
        assertEquals(info.getInstanceId(), allInfo.get(0).getInstanceId());

        val stopInstanceMessage = new StopInstanceMessage(MessageHeader.controllerRequest(),
                                                          executorAddress,
                                                          instanceId);
        assertEquals(MessageDeliveryStatus.ACCEPTED, engine.handleMessage(stopInstanceMessage).getStatus());
        waitUntil(() -> engine.currentState(instanceId).isEmpty());
        assertEquals(MessageDeliveryStatus.FAILED, engine.handleMessage(stopInstanceMessage).getStatus());
        val statesDiff = Sets.difference(stateChanges,
                                         EnumSet.of(PENDING,
                                                    PROVISIONING,
                                                    STARTING,
                                                    UNREADY,
                                                    READY,
                                                    HEALTHY,
                                                    DEPROVISIONING,
                                                    STOPPING,
                                                    STOPPED));
        assertTrue(statesDiff.isEmpty());
    }

    @Test
    void testBlacklisting() {
        assertFalse(blacklistingManager.isBlacklisted());
        engine.blacklist();
        assertTrue(blacklistingManager.isBlacklisted());
        engine.unblacklist();
        assertFalse(blacklistingManager.isBlacklisted());
    }

    @Test
    void testInvalidResourceAllocation() {
        val spec = new InstanceSpec("T001",
                                    "TEST_SPEC",
                                    UUID.randomUUID().toString(),
                                    new DockerCoordinates(
                                            "docker.io/santanusinha/perf-test-server:0.1",
                                            io.dropwizard.util.Duration.seconds(100)),
                                    ImmutableList.of(new CPUAllocation(Collections.singletonMap(0, Set.of(-1, -3))),
                                                     new MemoryAllocation(Collections.singletonMap(0, 512L))),
                                    Collections.singletonList(new PortSpec("main", 8000, PortType.HTTP)),
                                    Collections.emptyList(),
                                    new CheckSpec(new HTTPCheckModeSpec("http",
                                                                        "main",
                                                                        "/",
                                                                        HTTPVerb.GET,
                                                                        Collections.singleton(200),
                                                                        "",
                                                                        io.dropwizard.util.Duration.seconds(1)),
                                                  io.dropwizard.util.Duration.seconds(1),
                                                  io.dropwizard.util.Duration.seconds(3),
                                                  3,
                                                  io.dropwizard.util.Duration.seconds(0)),
                                    new CheckSpec(new HTTPCheckModeSpec("http",
                                                                        "main",
                                                                        "/",
                                                                        HTTPVerb.GET,
                                                                        Collections.singleton(200),
                                                                        "",
                                                                        io.dropwizard.util.Duration.seconds(1)),
                                                  io.dropwizard.util.Duration.seconds(1),
                                                  io.dropwizard.util.Duration.seconds(3),
                                                  3,
                                                  io.dropwizard.util.Duration.seconds(0)),
                                    LocalLoggingSpec.DEFAULT,
                                    Collections.emptyMap());
        val executorAddress = new ExecutorAddress("eid", "localhost", 3000);
        val startInstanceMessage = new StartInstanceMessage(MessageHeader.controllerRequest(),
                                                            executorAddress,
                                                            spec);
        val startResponse = engine.handleMessage(startInstanceMessage);
        assertEquals(MessageDeliveryStatus.FAILED, startResponse.getStatus());
    }

    @Test
    void testInvalidStop() {
        assertFalse(engine.stopInstance("test"));
    }
}