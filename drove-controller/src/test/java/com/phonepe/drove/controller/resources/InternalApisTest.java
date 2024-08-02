/*
 *  Copyright (c) 2022 Original Author(s), PhonePe India Pvt. Ltd.
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
import com.phonepe.drove.controller.resourcemgmt.ClusterResourcesDB;
import com.phonepe.drove.controller.statedb.ApplicationStateDB;
import com.phonepe.drove.controller.statedb.TaskDB;
import com.phonepe.drove.models.api.ApiErrorCode;
import com.phonepe.drove.models.api.ApiResponse;
import com.phonepe.drove.models.application.ApplicationInfo;
import com.phonepe.drove.models.instance.InstanceState;
import com.phonepe.drove.models.internal.KnownInstancesData;
import com.phonepe.drove.models.taskinstance.TaskState;
import io.dropwizard.testing.junit5.DropwizardExtensionsSupport;
import io.dropwizard.testing.junit5.ResourceExtension;
import lombok.val;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;

import javax.ws.rs.core.GenericType;
import java.util.List;
import java.util.Optional;

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

    private static final ResourceExtension EXT = ResourceExtension.builder()
            .addResource(new InternalApis(clusterResourcesDB, applicationStateDB, taskDB))
            .build();

    @AfterEach
    void reset() {
        Mockito.reset(applicationStateDB, clusterResourcesDB, taskDB);
    }

    @Test
    void testSnapshotApisSuccess() {
        val appSpec = ControllerTestUtils.appSpec();
        val taskSpec = ControllerTestUtils.taskSpec();
        val appId = "TEST_APP-1";
        val taskInfo = ControllerTestUtils.generateTaskInfo(
                taskSpec, 0, TaskState.RUNNING, null, null);
        val executorHostInfo = ControllerTestUtils.executorHost(
                3001,
                List.of(ControllerTestUtils.generateInstanceInfo(
                        appId, appSpec, 0, InstanceState.HEALTHY)),
                List.of(taskInfo));
        when(clusterResourcesDB.lastKnownSnapshot(anyString()))
                .thenReturn(Optional.of(
                        executorHostInfo));
        when(clusterResourcesDB.currentSnapshot(anyString()))
                .thenReturn(Optional.of(
                        executorHostInfo));
        when(applicationStateDB.application(appId)).thenReturn(Optional.of(new ApplicationInfo(appId,
                                                                                               appSpec,
                                                                                               1,
                                                                                               null,
                                                                                               null)));
        when(taskDB.task(eq(taskSpec.getSourceAppName()), anyString())).thenReturn(Optional.of(taskInfo));

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
        }
    }


    @Test
    void testSnapshotApisSuccessStale() {
        val appSpec = ControllerTestUtils.appSpec();
        val taskSpec = ControllerTestUtils.taskSpec();
        val appId = "TEST_APP-1";
        val taskInfo = ControllerTestUtils.generateTaskInfo(
                taskSpec, 0, TaskState.RUNNING, null, null);
        val executorHostInfo = ControllerTestUtils.executorHost(
                3001,
                List.of(ControllerTestUtils.generateInstanceInfo(
                        appId, appSpec, 0, InstanceState.HEALTHY)),
                List.of(taskInfo));
        when(clusterResourcesDB.lastKnownSnapshot(anyString()))
                .thenReturn(Optional.of(
                        executorHostInfo));
        when(clusterResourcesDB.currentSnapshot(anyString()))
                .thenReturn(Optional.of(
                        executorHostInfo));
        when(applicationStateDB.application(appId)).thenReturn(Optional.of(new ApplicationInfo(appId,
                                                                                               appSpec,
                                                                                               0,
                                                                                               null,
                                                                                               null)));
        {
            val r = EXT.target(
                            "/v1/internal/cluster/executors/" + executorHostInfo.getExecutorId() + "/instances/last")
                    .request()
                    .get(new GenericType<ApiResponse<KnownInstancesData>>() {
                    });
            assertEquals(ApiErrorCode.SUCCESS, r.getStatus());
            assertEquals(1, r.getData().getStaleAppInstanceIds().size());
            assertTrue(r.getData().getAppInstanceIds().isEmpty());
            assertEquals(1, r.getData().getStaleAppInstanceIds().size());
            assertTrue(r.getData().getTaskInstanceIds().isEmpty());
        }
        {
            val r = EXT.target(
                            "/v1/internal/cluster/executors/" + executorHostInfo.getExecutorId() + "/instances/current")
                    .request()
                    .get(new GenericType<ApiResponse<KnownInstancesData>>() {
                    });
            assertEquals(ApiErrorCode.SUCCESS, r.getStatus());
            assertEquals(ApiErrorCode.SUCCESS, r.getStatus());
            assertEquals(1, r.getData().getStaleAppInstanceIds().size());
            assertTrue(r.getData().getAppInstanceIds().isEmpty());
            assertEquals(1, r.getData().getStaleAppInstanceIds().size());
            assertTrue(r.getData().getTaskInstanceIds().isEmpty());
        }
    }
}