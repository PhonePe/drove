/*
 *  Copyright (c) 2024 Original Author(s), PhonePe India Pvt. Ltd.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package com.phonepe.drove.controller.managed;

import com.phonepe.drove.common.CommonTestUtils;
import com.phonepe.drove.controller.ControllerTestUtils;
import com.phonepe.drove.controller.engine.LocalServiceLifecycleManagementEngine;
import com.phonepe.drove.controller.engine.ValidationResult;
import com.phonepe.drove.controller.resourcemgmt.ClusterResourcesDB;
import com.phonepe.drove.controller.statedb.ClusterStateDB;
import com.phonepe.drove.controller.statedb.LocalServiceStateDB;
import com.phonepe.drove.controller.utils.ControllerUtils;
import com.phonepe.drove.models.common.ClusterState;
import com.phonepe.drove.models.common.ClusterStateData;
import com.phonepe.drove.models.info.nodedata.ExecutorNodeData;
import com.phonepe.drove.models.instance.LocalServiceInstanceState;
import com.phonepe.drove.models.localservice.ActivationState;
import com.phonepe.drove.models.localservice.LocalServiceInfo;
import com.phonepe.drove.models.localservice.LocalServiceState;
import com.phonepe.drove.models.operation.LocalServiceOperation;
import com.phonepe.drove.models.operation.LocalServiceOperationType;
import io.appform.signals.signals.ScheduledSignal;
import lombok.SneakyThrows;
import lombok.val;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import static com.phonepe.drove.controller.ControllerTestUtils.generateLocalServiceInstanceInfo;
import static com.phonepe.drove.controller.ControllerTestUtils.localServiceSpec;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

/**
 * Tests {@link LocalServiceMonitor}
 */
class LocalServiceMonitorTest {

    private final ClusterResourcesDB clusterResourcesDB = mock(ClusterResourcesDB.class);
    private final ClusterStateDB clusterStateDB = mock(ClusterStateDB.class);
    private final LocalServiceStateDB stateDB = mock(LocalServiceStateDB.class);
    private final LocalServiceLifecycleManagementEngine localServiceEngine =
            mock(LocalServiceLifecycleManagementEngine.class);
    private final LeadershipEnsurer leadershipEnsurer = mock(LeadershipEnsurer.class);
    private final LocalServiceMonitor monitor = new LocalServiceMonitor(
            clusterResourcesDB,
            clusterStateDB,
            stateDB,
            localServiceEngine,
            leadershipEnsurer,
            ScheduledSignal.builder()
                    .interval(Duration.ofMillis(300))
                    .build()
    );

    @AfterEach
    void resetMocks() {
        reset(clusterResourcesDB,
              clusterStateDB,
              stateDB,
              localServiceEngine,
              leadershipEnsurer);
    }

    @Test
    @SneakyThrows
    void testActiveScaleUp() {
        when(leadershipEnsurer.isLeader()).thenReturn(true);
        when(clusterStateDB.currentState())
                .thenReturn(Optional.of(new ClusterStateData(ClusterState.NORMAL,
                                                             Date.from(Instant.now().minus(1, ChronoUnit.DAYS)))));
        val spec = localServiceSpec();
        val serviceId = ControllerUtils.deployableObjectId(spec);
        final var instancesPerHost = 3;
        when(stateDB.services(0, Integer.MAX_VALUE))
                .thenReturn(List.of(new LocalServiceInfo(serviceId,
                                                         spec,
                                                         instancesPerHost,
                                                         ActivationState.ACTIVE,
                                                         Date.from(Instant.now().minus(1, ChronoUnit.HOURS)),
                                                         Date.from(Instant.now()))));
        when(localServiceEngine.currentState(serviceId)).thenReturn(Optional.of(LocalServiceState.ACTIVE));
        val numExecutors = 5;
        val liveExecutors = IntStream.range(0, numExecutors)
                .mapToObj(i -> ControllerTestUtils.executorHost(8000 + i))
                .toList();
        when(clusterResourcesDB.currentSnapshot(false)).thenReturn(liveExecutors);
        when(clusterResourcesDB.isBlacklisted(anyString())).thenReturn(false);
        val adjustCalled = new AtomicInteger(0);
        val instances = IntStream.range(0, numExecutors * instancesPerHost)
                .mapToObj(i -> ControllerTestUtils.generateInstanceInfo(
                        serviceId,
                        spec,
                        i, LocalServiceInstanceState.HEALTHY))
                .toList();
        //Return no instances at start, then correct instances when called again
        when(stateDB.instances(serviceId, LocalServiceInstanceState.ACTIVE_STATES, false))
                .thenAnswer(invocationOnMock -> adjustCalled.get() < 2 ? List.of() : instances);
        when(localServiceEngine.handleOperation(any(LocalServiceOperation.class)))
                .thenAnswer(invocationOnMock -> {
                    val op = (LocalServiceOperation) invocationOnMock.getArgument(0);
                    if (op.getType() == LocalServiceOperationType.ADJUST_INSTANCES) {
                        if(adjustCalled.getAndIncrement() == 0) {
                            return ValidationResult.failure("Test failure");
                        }
                        return ValidationResult.success();
                    }
                    throw new IllegalStateException("Unexpected operation of type: " + op.getType());
                });
        try {
            monitor.start();
            CommonTestUtils.delay(Duration.ofSeconds(1)); //Wait enough time for checks to be hit more than once
            assertEquals(2, adjustCalled.get()); //ensure adjust is called only once
        }
        finally {
            monitor.stop();
        }
    }

    @Test
    @SneakyThrows
    void testInactiveScaleDown() {
        when(leadershipEnsurer.isLeader()).thenReturn(true);
        when(clusterStateDB.currentState())
                .thenReturn(Optional.of(new ClusterStateData(ClusterState.NORMAL,
                                                             Date.from(Instant.now().minus(1, ChronoUnit.DAYS)))));
        val spec = localServiceSpec();
        val serviceId = ControllerUtils.deployableObjectId(spec);
        final var instancesPerHost = 3;
        when(stateDB.services(0, Integer.MAX_VALUE))
                .thenReturn(List.of(new LocalServiceInfo(serviceId,
                                                         spec,
                                                         instancesPerHost,
                                                         ActivationState.INACTIVE,
                                                         Date.from(Instant.now().minus(1, ChronoUnit.HOURS)),
                                                         Date.from(Instant.now()))));
        when(localServiceEngine.currentState(serviceId)).thenReturn(Optional.of(LocalServiceState.INACTIVE));
        val numExecutors = 5;
        val liveExecutors = IntStream.range(0, numExecutors)
                .mapToObj(i -> ControllerTestUtils.executorHost(8000 + i))
                .toList();
        when(clusterResourcesDB.currentSnapshot(false)).thenReturn(liveExecutors);
        when(clusterResourcesDB.isBlacklisted(anyString())).thenReturn(false);
        val adjustCalled = new AtomicInteger(0);
        val instances = IntStream.range(0, numExecutors * instancesPerHost)
                .mapToObj(i -> ControllerTestUtils.generateInstanceInfo(
                        serviceId,
                        spec,
                        i, LocalServiceInstanceState.HEALTHY))
                .toList();
        //Return correct instances at start, then no instances when called again
        when(stateDB.instances(serviceId, LocalServiceInstanceState.ACTIVE_STATES, false))
                .thenAnswer(invocationOnMock -> adjustCalled.get() == 0 ? instances : List.of());
        when(localServiceEngine.handleOperation(any(LocalServiceOperation.class)))
                .thenAnswer(invocationOnMock -> {
                    val op = (LocalServiceOperation) invocationOnMock.getArgument(0);
                    if (op.getType() == LocalServiceOperationType.ADJUST_INSTANCES) {
                        adjustCalled.incrementAndGet();
                    }
                    return ValidationResult.success();
                });
        try {
            monitor.start();
            CommonTestUtils.delay(Duration.ofSeconds(1)); //Wait enough time for checks to be hit more than once
            assertEquals(1, adjustCalled.get()); //ensure adjust is called only once
        }
        finally {
            monitor.stop();
        }
    }

    @Test
    @SneakyThrows
    void testBlacklistScaleDown() {
        when(leadershipEnsurer.isLeader()).thenReturn(true);
        when(clusterStateDB.currentState())
                .thenReturn(Optional.of(new ClusterStateData(ClusterState.NORMAL,
                                                             Date.from(Instant.now().minus(1, ChronoUnit.DAYS)))));
        val spec = localServiceSpec();
        val serviceId = ControllerUtils.deployableObjectId(spec);
        final var instancesPerHost = 3;
        when(stateDB.services(0, Integer.MAX_VALUE))
                .thenReturn(List.of(new LocalServiceInfo(serviceId,
                                                         spec,
                                                         instancesPerHost,
                                                         ActivationState.ACTIVE,
                                                         Date.from(Instant.now().minus(1, ChronoUnit.HOURS)),
                                                         Date.from(Instant.now()))));
        when(localServiceEngine.currentState(serviceId)).thenReturn(Optional.of(LocalServiceState.ACTIVE));
        val numExecutors = 5;
        val executorsWithoutInstances = IntStream.range(0, numExecutors)
                .mapToObj(i -> ControllerTestUtils.executorHost(8000 + i))
                .toList();
        val executorsWithInstances = IntStream.range(0, numExecutors)
                .mapToObj(i -> {
                    val executorHostInfo = ControllerTestUtils.executorHost(8000 + i);
                    final var nodeData = executorHostInfo.getNodeData();
                    return executorHostInfo
                            .setNodeData(new ExecutorNodeData(nodeData.getHostname(),
                                                              nodeData.getPort(),
                                                              nodeData.getTransportType(),
                                                              nodeData.getUpdated(),
                                                              nodeData.getState(),
                                                              nodeData.getInstances(),
                                                              nodeData.getTasks(),
                                                              List.of(generateLocalServiceInstanceInfo(
                                                                      serviceId,
                                                                      spec,
                                                                      i,
                                                                      LocalServiceInstanceState.HEALTHY,
                                                                      Date.from(Instant.now()),
                                                                      "")),
                                                              nodeData.getTags(),
                                                              nodeData.getExecutorState()));
                })
                .toList();
        val stopCalled = new AtomicInteger(0);
        when(clusterResourcesDB.currentSnapshot(false))
                .thenAnswer(invocationOnMock -> stopCalled.get() != 0
                                                ? executorsWithoutInstances
                                                : executorsWithInstances);
        when(clusterResourcesDB.isBlacklisted(anyString())).thenReturn(true);
        when(localServiceEngine.handleOperation(any(LocalServiceOperation.class)))
                .thenAnswer(invocationOnMock -> {
                    val op = (LocalServiceOperation) invocationOnMock.getArgument(0);
                    if (op.getType() == LocalServiceOperationType.STOP_INSTANCES) {
                        stopCalled.incrementAndGet();
                    }
                    return ValidationResult.success();
                });
        try {
            monitor.start();
            CommonTestUtils.delay(Duration.ofSeconds(1)); //Wait enough time for checks to be hit more than once
            assertEquals(1, stopCalled.get()); //ensure stop instances is called only once
        }
        finally {
            monitor.stop();
        }
    }


    @Test
    @SneakyThrows
    void testSkippedForNoLeader() {
        when(leadershipEnsurer.isLeader()).thenReturn(false);
        val stopCalled = new AtomicInteger(0);
        when(stateDB.services(0, Integer.MAX_VALUE))
                .thenAnswer(invocationOnMock -> {
                    stopCalled.incrementAndGet();
                    return List.of();
                });
        try {
            monitor.start();
            CommonTestUtils.delay(Duration.ofMillis(500)); //Wait enough time for checks to be hit more than once
            assertEquals(0, stopCalled.get()); //ensure stop instances is called only once
        }
        finally {
            monitor.stop();
        }
    }

    @Test
    @SneakyThrows
    void testSkippedForMaintenanceMode() {
        when(leadershipEnsurer.isLeader()).thenReturn(true);
        when(clusterStateDB.currentState())
                .thenReturn(Optional.of(new ClusterStateData(ClusterState.MAINTENANCE,
                                                             Date.from(Instant.now().minus(1, ChronoUnit.DAYS)))));
        val stopCalled = new AtomicInteger(0);
        when(stateDB.services(0, Integer.MAX_VALUE))
                .thenAnswer(invocationOnMock -> {
                    stopCalled.incrementAndGet();
                    return List.of();
                });
        try {
            monitor.start();
            CommonTestUtils.delay(Duration.ofMillis(500)); //Wait enough time for checks to be hit more than once
            assertEquals(0, stopCalled.get()); //ensure stop instances is called only once
        }
        finally {
            monitor.stop();
        }
    }
}