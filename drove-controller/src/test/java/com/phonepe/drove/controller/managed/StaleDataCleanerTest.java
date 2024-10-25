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
import com.phonepe.drove.controller.config.ControllerOptions;
import com.phonepe.drove.controller.engine.ApplicationLifecycleManagentEngine;
import com.phonepe.drove.controller.engine.TaskEngine;
import com.phonepe.drove.controller.engine.ValidationResult;
import com.phonepe.drove.controller.statedb.ApplicationInstanceInfoDB;
import com.phonepe.drove.controller.statedb.ApplicationStateDB;
import com.phonepe.drove.controller.statedb.TaskDB;
import com.phonepe.drove.controller.testsupport.InMemoryApplicationInstanceInfoDB;
import com.phonepe.drove.controller.testsupport.InMemoryApplicationStateDB;
import com.phonepe.drove.controller.testsupport.InMemoryTaskDB;
import com.phonepe.drove.controller.utils.ControllerUtils;
import com.phonepe.drove.models.application.ApplicationInfo;
import com.phonepe.drove.models.instance.InstanceState;
import com.phonepe.drove.models.operation.ops.ApplicationDestroyOperation;
import com.phonepe.drove.models.taskinstance.TaskState;
import lombok.val;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.IntStream;

import static com.phonepe.drove.controller.ControllerTestUtils.appSpec;
import static com.phonepe.drove.controller.config.ControllerOptions.DEFAULT;
import static com.phonepe.drove.controller.config.ControllerOptions.DEFAULT_MAX_STALE_INSTANCES_COUNT;
import static com.phonepe.drove.models.application.ApplicationState.MONITORING;
import static com.phonepe.drove.models.application.ApplicationState.RUNNING;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 *
 */
class StaleDataCleanerTest {

    @Test
    void testStaleAppCleanup() {
        val appStateDB = new InMemoryApplicationStateDB();
        val instanceDB = new InMemoryApplicationInstanceInfoDB();
        val taskDB = mock(TaskDB.class);
        val le = mock(LeadershipEnsurer.class);
        when(le.isLeader()).thenReturn(true);
        val engine = mock(ApplicationLifecycleManagentEngine.class);
        val taskEngine = mock(TaskEngine.class);

        val sdc = new StaleDataCleaner(appStateDB,
                                       instanceDB,
                                       taskDB,
                                       le,
                                       engine,
                                       ControllerOptions.DEFAULT,
                                       Duration.ofSeconds(1), ControllerTestUtils.DEFAULT_CLUSTER_OP);

        val spec = appSpec();
        val appId = ControllerUtils.deployableObjectId(spec);
        val oldDate = Date.from(LocalDate.now().minusDays(32).atStartOfDay(ZoneId.systemDefault()).toInstant());
        appStateDB.updateApplicationState(appId, new ApplicationInfo(appId, spec, 0, oldDate, oldDate));

        val testRun = new AtomicBoolean();
        when(engine.currentState(anyString())).thenReturn(Optional.of(MONITORING));
        when(engine.handleOperation(any(ApplicationDestroyOperation.class)))
                .thenAnswer(invocationOnMock -> {
                    val dId = invocationOnMock.getArgument(0, ApplicationDestroyOperation.class).getAppId();
                    testRun.set(dId.equals(appId));
                    appStateDB.deleteApplicationState(appId);
                    return ValidationResult.success();
                });
        sdc.start();
        await().atMost(Duration.ofMinutes(1))
                .until(testRun::get);
        sdc.stop();
        assertNull(appStateDB.application(appId).orElse(null));
    }

    @Test
    void testStaleInstanceCleanup() {
        val appStateDB = new InMemoryApplicationStateDB();
        val instanceDB = new InMemoryApplicationInstanceInfoDB();
        val taskDB = mock(TaskDB.class);
        val le = mock(LeadershipEnsurer.class);
        when(le.isLeader()).thenReturn(true);
        val engine = mock(ApplicationLifecycleManagentEngine.class);

        val sdc = new StaleDataCleaner(appStateDB,
                                       instanceDB,
                                       taskDB,
                                       le,
                                       engine,
                                       ControllerOptions.DEFAULT,
                                       Duration.ofSeconds(1), ControllerTestUtils.DEFAULT_CLUSTER_OP);

        val spec = appSpec();
        val appId = ControllerUtils.deployableObjectId(spec);
        val oldDate = Date.from(LocalDate.now().minusDays(32).atStartOfDay(ZoneId.systemDefault()).toInstant());
        appStateDB.updateApplicationState(appId, new ApplicationInfo(appId, spec, 0, oldDate, oldDate));
        IntStream.rangeClosed(1, 100)
                .forEach(i -> {
                    val instance = ControllerTestUtils.generateInstanceInfo(appId,
                                                                            spec,
                                                                            i,
                                                                            InstanceState.STOPPED,
                                                                            oldDate,
                                                                            null);
                    instanceDB.updateInstanceState(appId, instance.getInstanceId(), instance);
                });
        when(engine.currentState(anyString())).thenReturn(Optional.of(RUNNING));

        sdc.start();
        await().atMost(Duration.ofMinutes(1)).until(() -> instanceDB.oldInstances(appId, 0, Integer.MAX_VALUE)
                .isEmpty());
        sdc.stop();
    }

    @Test
    void testStaleInstanceCleanupByCount() {
        {
            testCleanupByInstanceCount(ControllerOptions.DEFAULT, DEFAULT_MAX_STALE_INSTANCES_COUNT);
        }
        {
            testCleanupByInstanceCount(DEFAULT.withMaxStaleInstancesCount(110), 110);
        }
    }

    @Test
    void testTaskCleanup() {
        val appStateDB = mock(ApplicationStateDB.class);
        val instanceDB = mock(ApplicationInstanceInfoDB.class);
        val taskDB = new InMemoryTaskDB();
        val le = mock(LeadershipEnsurer.class);
        when(le.isLeader()).thenReturn(true);
        val engine = mock(ApplicationLifecycleManagentEngine.class);

        val sdc = new StaleDataCleaner(appStateDB, instanceDB, taskDB, le, engine, DEFAULT, Duration.ofSeconds(1),
                                       ControllerTestUtils.DEFAULT_CLUSTER_OP);

        val oldDate = Date.from(LocalDate.now().minusDays(32).atStartOfDay(ZoneId.systemDefault()).toInstant());
        val appName = "TEST_TASK";
        generateTasks(taskDB, oldDate, appName, 0);
        generateTasks(taskDB, new Date(), appName, 101);
        sdc.start();
        await().atMost(Duration.ofMinutes(1))
                .until(() -> taskDB.tasks(Set.of(appName), EnumSet.allOf(TaskState.class), true)
                        .getOrDefault(appName, List.of())
                        .size() == 152);
        assertEquals(152, taskDB.tasks(Set.of(appName), EnumSet.allOf(TaskState.class), true)
                .getOrDefault(appName, List.of())
                .size());
        sdc.stop();
    }

    private static void generateTasks(InMemoryTaskDB taskDB, java.util.Date oldDate, String appName, int start) {
        IntStream.rangeClosed(start, start + 100)
                .forEach(i -> {
                    val spec = ControllerTestUtils.taskSpec(appName, i);
                    val instance = ControllerTestUtils.generateTaskInfo(spec,
                                                                        1,
                                                                        i % 2 == 0 ? TaskState.RUNNING
                                                                                   : TaskState.STOPPED,
                                                                        oldDate,
                                                                        "");
                    taskDB.updateTask(instance.getSourceAppName(), instance.getTaskId(), instance);
                });
    }

    private static void testCleanupByInstanceCount(ControllerOptions options, int expectedCount) {
        val appStateDB = new InMemoryApplicationStateDB();
        val instanceDB = new InMemoryApplicationInstanceInfoDB();
        val taskDB = mock(TaskDB.class);
        val le = mock(LeadershipEnsurer.class);
        when(le.isLeader()).thenReturn(true);
        val engine = mock(ApplicationLifecycleManagentEngine.class);

        val sdc = new StaleDataCleaner(appStateDB, instanceDB, taskDB, le, engine, options, Duration.ofSeconds(1),
                                       ControllerTestUtils.DEFAULT_CLUSTER_OP);

        val spec = appSpec();
        val appId = ControllerUtils.deployableObjectId(spec);
        val date = new java.util.Date();
        appStateDB.updateApplicationState(appId, new ApplicationInfo(appId, spec, 0, date, date));
        IntStream.rangeClosed(1, 250)
                .forEach(i -> {
                    val instance = ControllerTestUtils.generateInstanceInfo(appId, spec, i, InstanceState.STOPPED, date,
                                                                            null);
                    instanceDB.updateInstanceState(appId, instance.getInstanceId(), instance);
                });
        when(engine.currentState(anyString())).thenReturn(Optional.of(RUNNING));

        sdc.start();
        await().atMost(Duration.ofMinutes(1))
                .until(() -> instanceDB.oldInstances(appId, 0, Integer.MAX_VALUE).size() == expectedCount);
        sdc.stop();
    }

}