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

import com.phonepe.drove.controller.ControllerTestBase;
import com.phonepe.drove.controller.ControllerTestUtils;
import com.phonepe.drove.controller.engine.ApplicationLifecycleManagementEngine;
import com.phonepe.drove.controller.engine.LocalServiceLifecycleManagementEngine;
import com.phonepe.drove.controller.engine.TaskEngine;
import com.phonepe.drove.controller.engine.TaskRunner;
import com.phonepe.drove.controller.engine.ValidationResult;
import com.phonepe.drove.controller.statedb.ApplicationStateDB;
import com.phonepe.drove.controller.statedb.LocalServiceStateDB;
import com.phonepe.drove.controller.statedb.TaskDB;
import com.phonepe.drove.controller.utils.ControllerUtils;
import com.phonepe.drove.models.application.ApplicationInfo;
import com.phonepe.drove.models.localservice.ActivationState;
import com.phonepe.drove.models.localservice.LocalServiceInfo;
import com.phonepe.drove.models.operation.ApplicationOperation;
import com.phonepe.drove.models.operation.LocalServiceOperation;
import io.appform.signals.signals.ConsumingSyncSignal;
import lombok.val;
import org.junit.jupiter.api.Test;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static com.phonepe.drove.controller.utils.ControllerUtils.deployableObjectId;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests {@link AppRecovery}
 */
class AppRecoveryTest extends ControllerTestBase {

    @Test
    void testRecovery() {
        val le = mock(LeadershipEnsurer.class);
        val ae = mock(ApplicationLifecycleManagementEngine.class);
        val te = mock(TaskEngine.class);
        val asdb = mock(ApplicationStateDB.class);
        val tdb = mock(TaskDB.class);
        val lse = mock(LocalServiceLifecycleManagementEngine.class);
        val lsdb = mock(LocalServiceStateDB.class);

        val lsc = new ConsumingSyncSignal<Boolean>();
        when(le.onLeadershipStateChanged()).thenReturn(lsc);

        val ar = new AppRecovery(le, ae, te, lse, asdb, tdb, lsdb, ControllerTestUtils.DEFAULT_CLUSTER_OP);
        ar.start();
        val specs = generateASI(100);
        final var taskSpec = ControllerTestUtils.generateTaskInfo(
                ControllerTestUtils.taskSpec("TEST_SPEC", 1), 1);
        val tasks = Map.of("TEST_SPEC", List.of(taskSpec));
        val serviceSpecs = generateLSI(100);
        when(asdb.applications(0, Integer.MAX_VALUE)).thenReturn(specs);
        when(tdb.tasks(eq(List.of("TEST_SPEC")), any(), eq(true))).thenReturn(tasks);
        when(lsdb.services(0, Integer.MAX_VALUE)).thenReturn(serviceSpecs);
        val appIds = new HashSet<String>();
        when(ae.handleOperation(any(ApplicationOperation.class)))
                .thenAnswer(new Answer<ValidationResult>() {
                    @Override
                    public ValidationResult answer(InvocationOnMock invocationOnMock) throws Throwable {
                        appIds.add(deployableObjectId(invocationOnMock.getArgument(0, ApplicationOperation.class)));
                        return ValidationResult.success();
                    }
                });
        val tRunner = mock(TaskRunner.class);
        val runnerCreated = new AtomicBoolean(false);
        when(te.registerTaskRunner(anyString(), anyString()))
                .thenAnswer(new Answer<TaskRunner>() {
                    @Override
                    public TaskRunner answer(InvocationOnMock invocationOnMock) throws Throwable {
                        final var appId = invocationOnMock.getArgument(0, String.class);
                        final var taskId = invocationOnMock.getArgument(1, String.class);
                        if (taskId.equals(taskSpec.getTaskId())) {
                            runnerCreated.set(true);
                            return tRunner;
                        }
                        throw new IllegalArgumentException("Wrong params");
                    }
                });
        val serviceIds = new HashSet<String>();
        when(lse.handleOperation(any(LocalServiceOperation.class)))
                .thenAnswer(new Answer<ValidationResult>() {
                    @Override
                    public ValidationResult answer(InvocationOnMock invocationOnMock) throws Throwable {
                        serviceIds.add(deployableObjectId(invocationOnMock.getArgument(0,
                                                                                       LocalServiceOperation.class)));
                        return ValidationResult.success();
                    }
                });
        lsc.dispatch(true);
        assertEquals(specs.stream().map(ApplicationInfo::getAppId).collect(Collectors.toSet()), appIds);
        assertTrue(runnerCreated.get());
        assertEquals(serviceSpecs.stream().map(LocalServiceInfo::getServiceId).collect(Collectors.toSet()), serviceIds);
        ar.stop();
    }

    @Test
    void testNoRecoveryNotLeader() {
        val le = mock(LeadershipEnsurer.class);
        val ae = mock(ApplicationLifecycleManagementEngine.class);
        val te = mock(TaskEngine.class);
        val asdb = mock(ApplicationStateDB.class);
        val tdb = mock(TaskDB.class);
        val lse = mock(LocalServiceLifecycleManagementEngine.class);
        val lsdb = mock(LocalServiceStateDB.class);

        val lsc = new ConsumingSyncSignal<Boolean>();
        when(le.onLeadershipStateChanged()).thenReturn(lsc);

        val ar = new AppRecovery(le, ae, te, lse, asdb, tdb, lsdb, ControllerTestUtils.DEFAULT_CLUSTER_OP);
        ar.start();
        val specs = generateASI(100);
        val serviceSpecs = generateLSI(100);
        when(asdb.applications(0, Integer.MAX_VALUE)).thenReturn(specs);
        when(tdb.tasks(any(), any(), anyBoolean())).thenReturn(Map.of());
        when(lsdb.services(0, Integer.MAX_VALUE)).thenReturn(serviceSpecs);
        when(ae.handleOperation(any(ApplicationOperation.class)))
                .thenThrow(new IllegalStateException("Should not have been called"));
        when(lse.handleOperation(any(LocalServiceOperation.class)))
                .thenThrow(new IllegalStateException("Should not have been called"));
        try {
            lsc.dispatch(false);
        }
        catch (IllegalStateException e) {
            fail();
        }

        ar.stop();
    }

    private static List<ApplicationInfo> generateASI(int numinstances) {
        return IntStream.rangeClosed(1, numinstances)
                .mapToObj(ControllerTestUtils::appSpec)
                .map(spec -> new ApplicationInfo(ControllerUtils.deployableObjectId(spec),
                                                 spec,
                                                 10,
                                                 new Date(),
                                                 new Date()))
                .toList();
    }

    private static List<LocalServiceInfo> generateLSI(int numInstances) {
        return IntStream.rangeClosed(1, 100)
                .mapToObj(ControllerTestUtils::localServiceSpec)
                .map(spec -> new LocalServiceInfo(ControllerUtils.deployableObjectId(spec),
                                                  spec,
                                                  1,
                                                  ActivationState.ACTIVE,
                                                  new Date(),
                                                  new Date()))
                .toList();
    }

    @Test
    void testAppRecoveryFailure() {
        val le = mock(LeadershipEnsurer.class);
        val ae = mock(ApplicationLifecycleManagementEngine.class);
        val te = mock(TaskEngine.class);
        val asdb = mock(ApplicationStateDB.class);
        val tdb = mock(TaskDB.class);
        val lse = mock(LocalServiceLifecycleManagementEngine.class);
        val lsdb = mock(LocalServiceStateDB.class);

        val lsc = new ConsumingSyncSignal<Boolean>();
        when(le.onLeadershipStateChanged()).thenReturn(lsc);

        val ar = new AppRecovery(le, ae, te, lse, asdb, tdb, lsdb, ControllerTestUtils.DEFAULT_CLUSTER_OP);
        ar.start();
        val appInstances = generateASI(1);
        final var taskSpec = ControllerTestUtils.generateTaskInfo(
                ControllerTestUtils.taskSpec("TEST_SPEC", 1), 1);
        val tasks = Map.of("TEST_SPEC", List.of(taskSpec));
        val serviceInstances = generateLSI(1);
        when(asdb.applications(0, Integer.MAX_VALUE)).thenReturn(appInstances);
        when(tdb.tasks(eq(List.of("TEST_SPEC")), any(), eq(true))).thenReturn(tasks);
        when(lsdb.services(0, Integer.MAX_VALUE)).thenReturn(serviceInstances);

        val aeCalled = new AtomicBoolean(false);
        val lseCalled = new AtomicBoolean(false);
        when(ae.handleOperation(any(ApplicationOperation.class)))
                .thenAnswer(new Answer<ValidationResult>() {
                    @Override
                    public ValidationResult answer(InvocationOnMock invocationOnMock) {
                        aeCalled.set(true);
                        return ValidationResult.failure("AE failure");
                    }
                });
        val runnerCreated = new AtomicBoolean(false);
        when(te.registerTaskRunner(anyString(), anyString()))
                .thenAnswer(new Answer<TaskRunner>() {
                    @Override
                    public TaskRunner answer(InvocationOnMock invocationOnMock) throws Throwable {
                        final var appId = invocationOnMock.getArgument(0, String.class);
                        final var taskId = invocationOnMock.getArgument(1, String.class);
                        if (taskId.equals(taskSpec.getTaskId())) {
                            runnerCreated.set(true);
                            return null;
                        }
                        throw new IllegalArgumentException("Wrong params");
                    }
                });
        when(lse.handleOperation(any(LocalServiceOperation.class)))
                .thenAnswer(new Answer<ValidationResult>() {
                    @Override
                    public ValidationResult answer(InvocationOnMock invocationOnMock) {
                        lseCalled.set(true);
                        return ValidationResult.failure("LSE failure");
                    }
                });
        lsc.dispatch(true);
        assertTrue(aeCalled.get());
        assertTrue(runnerCreated.get());
        assertTrue(lseCalled.get());
        ar.stop();

    }

    @Test
    void testAppRecoveryException() {
        val le = mock(LeadershipEnsurer.class);
        val ae = mock(ApplicationLifecycleManagementEngine.class);
        val te = mock(TaskEngine.class);
        val asdb = mock(ApplicationStateDB.class);
        val tdb = mock(TaskDB.class);
        val lse = mock(LocalServiceLifecycleManagementEngine.class);
        val lsdb = mock(LocalServiceStateDB.class);

        val lsc = new ConsumingSyncSignal<Boolean>();
        when(le.onLeadershipStateChanged()).thenReturn(lsc);

        val ar = new AppRecovery(le, ae, te, lse, asdb, tdb, lsdb, ControllerTestUtils.DEFAULT_CLUSTER_OP);
        ar.start();
        val appInstances = generateASI(1);
        final var taskSpec = ControllerTestUtils.generateTaskInfo(
                ControllerTestUtils.taskSpec("TEST_SPEC", 1), 1);
        val tasks = Map.of("TEST_SPEC", List.of(taskSpec));
        val serviceInstances = generateLSI(1);
        when(asdb.applications(0, Integer.MAX_VALUE)).thenReturn(appInstances);
        when(tdb.tasks(eq(List.of("TEST_SPEC")), any(), eq(true))).thenReturn(tasks);
        when(lsdb.services(0, Integer.MAX_VALUE)).thenReturn(serviceInstances);

        val aeCalled = new AtomicBoolean(false);
        val lseCalled = new AtomicBoolean(false);
        when(ae.handleOperation(any(ApplicationOperation.class)))
                .thenAnswer(new Answer<ValidationResult>() {
                    @Override
                    public ValidationResult answer(InvocationOnMock invocationOnMock) {
                        aeCalled.set(true);
                        throw new RuntimeException("AE failure");
                    }
                });
        val runnerCreated = new AtomicBoolean(false);
        when(te.registerTaskRunner(anyString(), anyString()))
                .thenAnswer(new Answer<TaskRunner>() {
                    @Override
                    public TaskRunner answer(InvocationOnMock invocationOnMock) throws Throwable {
                        final var appId = invocationOnMock.getArgument(0, String.class);
                        final var taskId = invocationOnMock.getArgument(1, String.class);
                        if (taskId.equals(taskSpec.getTaskId())) {
                            runnerCreated.set(true);
                            throw new RuntimeException("Test");
                        }
                        throw new IllegalArgumentException("Wrong params");
                    }
                });
        when(lse.handleOperation(any(LocalServiceOperation.class)))
                .thenAnswer(new Answer<ValidationResult>() {
                    @Override
                    public ValidationResult answer(InvocationOnMock invocationOnMock) {
                        lseCalled.set(true);
                        throw new RuntimeException("Test");
                    }
                });
        lsc.dispatch(true);
        assertTrue(aeCalled.get());
        assertTrue(runnerCreated.get());
        assertTrue(lseCalled.get());
        ar.stop();

    }
}