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

package com.phonepe.drove.controller.resources;

import com.phonepe.drove.controller.ControllerTestUtils;
import com.phonepe.drove.controller.engine.LocalServiceLifecycleManagementEngine;
import com.phonepe.drove.controller.resourcemgmt.ClusterResourcesDB;
import com.phonepe.drove.controller.statedb.ApplicationStateDB;
import com.phonepe.drove.controller.statedb.LocalServiceStateDB;
import com.phonepe.drove.controller.statedb.TaskDB;
import com.phonepe.drove.controller.utils.ControllerUtils;
import com.phonepe.drove.models.api.ApiErrorCode;
import com.phonepe.drove.models.api.ApiResponse;
import com.phonepe.drove.models.application.ApplicationInfo;
import com.phonepe.drove.models.instance.InstanceState;
import com.phonepe.drove.models.instance.LocalServiceInstanceState;
import com.phonepe.drove.models.internal.KnownInstancesData;
import com.phonepe.drove.models.internal.LocalServiceInstanceResources;
import com.phonepe.drove.models.localservice.*;
import com.phonepe.drove.models.taskinstance.TaskState;
import io.dropwizard.testing.junit5.DropwizardExtensionsSupport;
import io.dropwizard.testing.junit5.ResourceExtension;
import lombok.val;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;

import javax.ws.rs.core.GenericType;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 *
 */
@ExtendWith(DropwizardExtensionsSupport.class)
class InternalApisTest {
    private static final ApplicationStateDB applicationStateDB = mock(ApplicationStateDB.class);
    private static final ClusterResourcesDB clusterResourcesDB = mock(ClusterResourcesDB.class);
    private static final TaskDB taskDB = mock(TaskDB.class);
    private static final LocalServiceStateDB localServicesDB = mock(LocalServiceStateDB.class);
    private static final LocalServiceLifecycleManagementEngine localServiceEngine = mock(
            LocalServiceLifecycleManagementEngine.class);

    private static final ResourceExtension EXT = ResourceExtension.builder()
            .addResource(new InternalApis(clusterResourcesDB,
                                          applicationStateDB,
                                          taskDB,
                                          localServicesDB,
                                          localServiceEngine))
            .build();

    @AfterEach
    void reset() {
        Mockito.reset(applicationStateDB, clusterResourcesDB, taskDB, localServicesDB);
    }

    @Test
    void testSnapshotApisSuccess() {
        val appSpec = ControllerTestUtils.appSpec();
        val taskSpec = ControllerTestUtils.taskSpec();
        val serviceSpec = ControllerTestUtils.localServiceSpec();
        val appId = ControllerUtils.deployableObjectId(appSpec);
        val serviceId = ControllerUtils.deployableObjectId(serviceSpec);
        val taskInfo = ControllerTestUtils.generateTaskInfo(
                taskSpec, 0, TaskState.RUNNING, null, null);
        val serviceInstanceInfo = ControllerTestUtils.generateLocalServiceInstanceInfo(serviceSpec);
        val executorHostInfo = ControllerTestUtils.executorHost(
                3001,
                List.of(ControllerTestUtils.generateInstanceInfo(
                        appId, appSpec, 0, InstanceState.HEALTHY)),
                List.of(taskInfo),
                List.of(serviceInstanceInfo));
        when(clusterResourcesDB.lastKnownSnapshot(anyString())).thenReturn(Optional.of(executorHostInfo));
        when(clusterResourcesDB.currentSnapshot(anyString())).thenReturn(Optional.of(executorHostInfo));
        when(applicationStateDB.application(appId)).thenReturn(Optional.of(new ApplicationInfo(appId,
                                                                                               appSpec,
                                                                                               1,
                                                                                               null,
                                                                                               null)));
        when(taskDB.task(eq(taskSpec.getSourceAppName()), anyString())).thenReturn(Optional.of(taskInfo));
        when(localServicesDB.service(anyString()))
                .thenReturn(Optional.of(new LocalServiceInfo(serviceId,
                                                             serviceSpec,
                                                             1,
                                                             ActivationState.ACTIVE,
                                                             new Date(),
                                                             new Date())));
        {
            val r = EXT.target(
                            "/v1/internal/cluster/executors/" + executorHostInfo.getExecutorId() + "/instances/last")
                    .request()
                    .get(new GenericType<ApiResponse<KnownInstancesData>>() {
                    });
            assertEquals(ApiErrorCode.SUCCESS, r.getStatus());
            assertEquals(1, r.getData().getAppInstanceIds().size());
            assertTrue(r.getData().getStaleAppInstanceIds().isEmpty());
            assertEquals(1, r.getData().getTaskInstanceIds().size());
            assertTrue(r.getData().getStaleTaskInstanceIds().isEmpty());
            assertEquals(1, r.getData().getLocalServiceInstanceIds().size());
            assertTrue(r.getData().getStaleLocalServiceInstanceIds().isEmpty());
        }
        {
            val r = EXT.target(
                            "/v1/internal/cluster/executors/" + executorHostInfo.getExecutorId() + "/instances/current")
                    .request()
                    .get(new GenericType<ApiResponse<KnownInstancesData>>() {
                    });
            assertEquals(ApiErrorCode.SUCCESS, r.getStatus());
            assertEquals(1, r.getData().getAppInstanceIds().size());
            assertTrue(r.getData().getStaleAppInstanceIds().isEmpty());
            assertEquals(1, r.getData().getTaskInstanceIds().size());
            assertTrue(r.getData().getStaleTaskInstanceIds().isEmpty());
            assertEquals(1, r.getData().getLocalServiceInstanceIds().size());
            assertTrue(r.getData().getStaleLocalServiceInstanceIds().isEmpty());
        }
    }


    @Test
    void testSnapshotApisSuccessStale() {
        val appSpec = ControllerTestUtils.appSpec();
        val taskSpec = ControllerTestUtils.taskSpec();
        val appId = "TEST_APP-1";
        val taskInfo = ControllerTestUtils.generateTaskInfo(
                taskSpec, 0, TaskState.RUNNING, null, null);

        val testInactiveService = ControllerTestUtils.localServiceSpec("TEST_INACTIVE_SERVICE", 1);
        val testInvalidService = generateServiceInstances(ControllerTestUtils.localServiceSpec(
                "TEST_INVALID_SERVICE",
                1));
        val testExtraService = ControllerTestUtils.localServiceSpec("TEST_EXTRA_SERVICE", 1);
        val lsInfos = new ArrayList<>(generateServiceInstances(testInactiveService));
        lsInfos.addAll(testInvalidService);
        lsInfos.addAll(generateServiceInstances(testExtraService));
        val executorHostInfo = ControllerTestUtils.executorHost(
                3001,
                List.of(ControllerTestUtils.generateInstanceInfo(appId, appSpec, 0, InstanceState.HEALTHY)),
                List.of(taskInfo),
                lsInfos);
        when(clusterResourcesDB.lastKnownSnapshot(anyString())).thenReturn(Optional.of(executorHostInfo));
        when(clusterResourcesDB.currentSnapshot(anyString())).thenReturn(Optional.of(executorHostInfo));
        when(applicationStateDB.application(appId))
                .thenReturn(Optional.of(new ApplicationInfo(appId,
                                                            appSpec,
                                                            0,
                                                            null,
                                                            null)));
        when(localServicesDB.service(anyString()))
                .thenAnswer(invocationOnMock -> {
                    val id = invocationOnMock.getArgument(0, String.class);
                    val extraServiceId = ControllerUtils.deployableObjectId(testExtraService);
                    val inactiveServiceId = ControllerUtils.deployableObjectId(testInactiveService);
                    if (id.equals(extraServiceId)) {
                        return Optional.of(new LocalServiceInfo(extraServiceId,
                                                                testExtraService,
                                                                1, //1 instance will be extra
                                                                ActivationState.ACTIVE,
                                                                new Date(),
                                                                new Date()));
                    }
                    if (id.equals(inactiveServiceId)) {
                        return Optional.of(new LocalServiceInfo(inactiveServiceId,
                                                                testInactiveService,
                                                                1,
                                                                ActivationState.INACTIVE, //2 instances will be extra
                                                                new Date(),
                                                                new Date()));
                    }
                    return Optional.empty();
                });
        {
            val r = EXT.target(
                            "/v1/internal/cluster/executors/" + executorHostInfo.getExecutorId() + "/instances" +
                                    "/last")
                    .request()
                    .get(new GenericType<ApiResponse<KnownInstancesData>>() {
                    });
            assertEquals(ApiErrorCode.SUCCESS, r.getStatus());
            assertEquals(1, r.getData().getStaleAppInstanceIds().size());
            assertTrue(r.getData().getAppInstanceIds().isEmpty());
            assertEquals(1, r.getData().getStaleTaskInstanceIds().size());
            assertTrue(r.getData().getTaskInstanceIds().isEmpty());
            assertEquals(5, r.getData().getStaleLocalServiceInstanceIds().size());
            assertEquals(1, r.getData().getLocalServiceInstanceIds().size());
        }
        {
            val r = EXT.target(
                            "/v1/internal/cluster/executors/" + executorHostInfo.getExecutorId() + "/instances" +
                                    "/current")
                    .request()
                    .get(new GenericType<ApiResponse<KnownInstancesData>>() {
                    });
            assertEquals(ApiErrorCode.SUCCESS, r.getStatus());
            assertEquals(1, r.getData().getStaleAppInstanceIds().size());
            assertTrue(r.getData().getAppInstanceIds().isEmpty());
            assertEquals(1, r.getData().getStaleTaskInstanceIds().size());
            assertTrue(r.getData().getTaskInstanceIds().isEmpty());
            assertEquals(5, r.getData().getStaleLocalServiceInstanceIds().size());
            assertEquals(1, r.getData().getLocalServiceInstanceIds().size());
        }
    }

    @Test
    void testReservedResources() {
        val activeService = ControllerTestUtils.localServiceSpec("ACTIVE_SERVICE", 1);
        val activeServiceId = ControllerUtils.deployableObjectId(activeService);
        val inActiveService = ControllerTestUtils.localServiceSpec("INACTIVE_SERVICE", 1);
        val inActiveServiceId = ControllerUtils.deployableObjectId(activeService);
        when(localServicesDB.services(0, Integer.MAX_VALUE))
                .thenReturn(List.of(new LocalServiceInfo(activeServiceId,
                                                          activeService,
                                                          1,
                                                          ActivationState.ACTIVE,
                                                          new Date(),
                                                          new Date()),
                                    new LocalServiceInfo(inActiveServiceId,
                                                         inActiveService,
                                                         1,
                                                         ActivationState.INACTIVE,
                                                         new Date(),
                                                         new Date())));
        when(localServiceEngine.currentState(anyString()))
                .thenAnswer(invocationOnMock -> {
                    val id = invocationOnMock.getArgument(0, String.class);
                    if(id.equals(activeServiceId)) {
                        return Optional.of(LocalServiceState.ACTIVE);
                    }
                    if(id.equals(inActiveServiceId)) {
                        return Optional.of(LocalServiceState.INACTIVE);
                    }
                    return Optional.empty();
                });
        val r = EXT.target("/v1/internal/cluster/resources/reserved")
                .request()
                .get(new GenericType<ApiResponse<LocalServiceInstanceResources>>() {
                });
        assertEquals(ApiErrorCode.SUCCESS, r.getStatus());
        assertEquals(1, r.getData().getRequiredInstances().size());
        assertEquals(1, r.getData().getRequiredInstances().getOrDefault(activeServiceId, -1));
    }

    private static List<LocalServiceInstanceInfo> generateServiceInstances(LocalServiceSpec serviceSpec) {
        return IntStream.rangeClosed(0, 1)
                .mapToObj(i -> ControllerTestUtils.generateLocalServiceInstanceInfo(
                        ControllerUtils.deployableObjectId(serviceSpec),
                        serviceSpec,
                        i,
                        LocalServiceInstanceState.HEALTHY,
                        new Date(),
                        ""))
                .toList();
    }
}