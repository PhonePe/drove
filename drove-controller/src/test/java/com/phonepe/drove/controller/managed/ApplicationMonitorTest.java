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

import com.phonepe.drove.controller.ControllerTestUtils;
import com.phonepe.drove.controller.engine.ApplicationLifecycleManagementEngine;
import com.phonepe.drove.controller.engine.ValidationResult;
import com.phonepe.drove.controller.statedb.ApplicationStateDB;
import com.phonepe.drove.controller.statedb.ClusterStateDB;
import com.phonepe.drove.controller.statedb.ApplicationInstanceInfoDB;
import com.phonepe.drove.controller.utils.ControllerUtils;
import com.phonepe.drove.models.application.ApplicationInfo;
import com.phonepe.drove.models.common.ClusterState;
import com.phonepe.drove.models.common.ClusterStateData;
import com.phonepe.drove.models.operation.ApplicationOperation;
import com.phonepe.drove.models.operation.ops.ApplicationRecoverOperation;
import lombok.SneakyThrows;
import lombok.val;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.stubbing.Answer;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static com.phonepe.drove.controller.utils.ControllerUtils.deployableObjectId;
import static com.phonepe.drove.models.application.ApplicationState.*;
import static com.phonepe.drove.models.instance.InstanceState.HEALTHY;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 *
 */
class ApplicationMonitorTest {

    @Test
    @SneakyThrows
    void testAllRunningPass() {
        val stateDB = mock(ApplicationStateDB.class);
        val instanceInfoDB = mock(ApplicationInstanceInfoDB.class);
        val clusterStateDB = mock(ClusterStateDB.class);
        val appEngine = mock(ApplicationLifecycleManagementEngine.class);
        val leadershipEnsurer = mock(LeadershipEnsurer.class);
        when(leadershipEnsurer.isLeader()).thenReturn(true);

        val monitor = new ApplicationMonitor(stateDB, instanceInfoDB, clusterStateDB, appEngine, leadershipEnsurer);

        val specs = IntStream.rangeClosed(1, 100)
                .mapToObj(ControllerTestUtils::appSpec)
                .map(spec -> new ApplicationInfo(ControllerUtils.deployableObjectId(spec), spec, 10, new Date(), new Date()))
                .toList();
        when(stateDB.applications(0, Integer.MAX_VALUE)).thenReturn(specs);
        when(appEngine.currentState(anyString())).thenReturn(Optional.of(RUNNING));
        when(instanceInfoDB.instanceCount(ArgumentMatchers.<Collection<String>>any(), eq(HEALTHY))).thenAnswer(
                invocationOnMock -> ((Collection<String>)invocationOnMock.getArgument(0, Collection.class))
                        .stream()
                        .collect(Collectors.toMap(Function.identity(), i -> 10L)));
        when(clusterStateDB.currentState()).thenReturn(Optional.of(new ClusterStateData(ClusterState.NORMAL, new Date(0))));
        val ids = new ArrayList<String>();
        when(appEngine.handleOperation(any(ApplicationRecoverOperation.class)))
                .thenAnswer((Answer<ValidationResult>) invocationOnMock -> {
                    ids.add(invocationOnMock.getArgument(0, ApplicationRecoverOperation.class).getAppId());
                    return ValidationResult.success();
                });
        monitor.checkAllApps(new Date());

        assertTrue(ids.isEmpty());
    }

    @Test
    @SneakyThrows
    void testAllMonitoringPass() {
        val stateDB = mock(ApplicationStateDB.class);
        val instanceInfoDB = mock(ApplicationInstanceInfoDB.class);
        val clusterStateDB = mock(ClusterStateDB.class);
        val appEngine = mock(ApplicationLifecycleManagementEngine.class);
        val leadershipEnsurer = mock(LeadershipEnsurer.class);
        when(leadershipEnsurer.isLeader()).thenReturn(true);

        val monitor = new ApplicationMonitor(stateDB, instanceInfoDB, clusterStateDB, appEngine, leadershipEnsurer);

        val specs = IntStream.rangeClosed(1, 100)
                .mapToObj(ControllerTestUtils::appSpec)
                .map(spec -> new ApplicationInfo(ControllerUtils.deployableObjectId(spec), spec, 0, new Date(), new Date()))
                .toList();
        when(stateDB.applications(0, Integer.MAX_VALUE)).thenReturn(specs);
        when(appEngine.currentState(anyString())).thenReturn(Optional.of(MONITORING));
        when(instanceInfoDB.instanceCount(ArgumentMatchers.<Collection<String>>any(), eq(HEALTHY))).thenAnswer(
                invocationOnMock -> ((Collection<String>)invocationOnMock.getArgument(0, Collection.class))
                        .stream()
                        .collect(Collectors.toMap(Function.identity(), i -> 0L)));
        when(clusterStateDB.currentState()).thenReturn(Optional.of(new ClusterStateData(ClusterState.NORMAL, new Date(0))));
        val ids = new ArrayList<String>();
        when(appEngine.handleOperation(any(ApplicationOperation.class)))
                .thenAnswer((Answer<ValidationResult>) invocationOnMock -> {
                    ids.add(invocationOnMock.getArgument(0, String.class));
                    return ValidationResult.success();
                });
        monitor.checkAllApps(new Date());

        assertTrue(ids.isEmpty());
    }

    @Test
    @SneakyThrows
    void testAllScale() {
        val stateDB = mock(ApplicationStateDB.class);
        val instanceInfoDB = mock(ApplicationInstanceInfoDB.class);
        val clusterStateDB = mock(ClusterStateDB.class);
        val appEngine = mock(ApplicationLifecycleManagementEngine.class);
        val leadershipEnsurer = mock(LeadershipEnsurer.class);
        when(leadershipEnsurer.isLeader()).thenReturn(true);

        val monitor = new ApplicationMonitor(stateDB, instanceInfoDB, clusterStateDB, appEngine, leadershipEnsurer);

        val specs = IntStream.rangeClosed(1, 100)
                .mapToObj(ControllerTestUtils::appSpec)
                .map(spec -> new ApplicationInfo(ControllerUtils.deployableObjectId(spec), spec, 10, new Date(), new Date()))
                .toList();
        when(stateDB.applications(0, Integer.MAX_VALUE)).thenReturn(specs);
        when(appEngine.currentState(anyString())).thenReturn(Optional.of(RUNNING));
        when(instanceInfoDB.instanceCount(ArgumentMatchers.<Collection<String>>any(), eq(HEALTHY))).thenAnswer(
                invocationOnMock -> ((Collection<String>)invocationOnMock.getArgument(0, Collection.class))
                        .stream()
                        .collect(Collectors.toMap(Function.identity(), i -> 0L)));
        when(clusterStateDB.currentState()).thenReturn(Optional.of(new ClusterStateData(ClusterState.NORMAL, new Date(0))));
        val ids = new HashSet<String>();
        when(appEngine.handleOperation(any(ApplicationOperation.class)))
                .thenAnswer((Answer<ValidationResult>) invocationOnMock -> {
                    ids.add(deployableObjectId(invocationOnMock.getArgument(0, ApplicationOperation.class)));
                    return ValidationResult.success();
                });
        monitor.checkAllApps(new Date());

        assertEquals(specs.stream().map(ApplicationInfo::getAppId).collect(Collectors.toSet()), ids);


    }

    @Test
    @SneakyThrows
    void testAllIgnore() {
        val stateDB = mock(ApplicationStateDB.class);
        val instanceInfoDB = mock(ApplicationInstanceInfoDB.class);
        val clusterStateDB = mock(ClusterStateDB.class);
        val appEngine = mock(ApplicationLifecycleManagementEngine.class);
        val leadershipEnsurer = mock(LeadershipEnsurer.class);
        when(leadershipEnsurer.isLeader()).thenReturn(true);

        val monitor = new ApplicationMonitor(stateDB, instanceInfoDB, clusterStateDB, appEngine, leadershipEnsurer);

        val specs = IntStream.rangeClosed(1, 100)
                .mapToObj(ControllerTestUtils::appSpec)
                .map(spec -> new ApplicationInfo(ControllerUtils.deployableObjectId(spec), spec, 10, new Date(), new Date()))
                .toList();
        when(stateDB.applications(0, Integer.MAX_VALUE)).thenReturn(specs);
        when(appEngine.currentState(anyString())).thenReturn(Optional.of(DESTROY_REQUESTED));
        when(instanceInfoDB.instanceCount(anyString(), eq(HEALTHY))).thenReturn(9L);
        when(clusterStateDB.currentState()).thenReturn(Optional.of(new ClusterStateData(ClusterState.NORMAL, new Date(0))));
        val ids = new HashSet<String>();
        when(appEngine.handleOperation(any(ApplicationOperation.class)))
                .thenAnswer((Answer<ValidationResult>) invocationOnMock -> {
                    ids.add(deployableObjectId(invocationOnMock.getArgument(0, ApplicationOperation.class)));
                    return ValidationResult.success();
                });
        monitor.checkAllApps(new Date());

        assertTrue(ids.isEmpty()); //No scale even if instance count mismatch
    }

    @Test
    @SneakyThrows
    void testNonLeader() {
        val stateDB = mock(ApplicationStateDB.class);
        val instanceInfoDB = mock(ApplicationInstanceInfoDB.class);
        val clusterStateDB = mock(ClusterStateDB.class);
        val appEngine = mock(ApplicationLifecycleManagementEngine.class);
        val leadershipEnsurer = mock(LeadershipEnsurer.class);
        when(leadershipEnsurer.isLeader()).thenReturn(false);

        val checked = new AtomicBoolean();
        when(stateDB.applications(anyInt(), anyInt())).thenAnswer(invocationOnMock -> {
            checked.set(true);
            return List.of();
        });

        val monitor = new ApplicationMonitor(stateDB, instanceInfoDB, clusterStateDB, appEngine, leadershipEnsurer);
        monitor.start();
        assertFalse(checked.get());

    }
}