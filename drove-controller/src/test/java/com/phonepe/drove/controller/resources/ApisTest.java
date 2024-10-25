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

import com.phonepe.drove.auth.core.DroveAuthorizer;
import com.phonepe.drove.auth.filters.DummyAuthFilter;
import com.phonepe.drove.auth.model.DroveUser;
import com.phonepe.drove.controller.ControllerTestUtils;
import com.phonepe.drove.controller.engine.ApplicationLifecycleManagementEngine;
import com.phonepe.drove.controller.engine.TaskEngine;
import com.phonepe.drove.controller.statedb.ClusterStateDB;
import com.phonepe.drove.controller.utils.ControllerUtils;
import com.phonepe.drove.controller.utils.EventUtils;
import com.phonepe.drove.models.api.*;
import com.phonepe.drove.models.application.ApplicationSpec;
import com.phonepe.drove.models.application.ApplicationState;
import com.phonepe.drove.models.common.ClusterState;
import com.phonepe.drove.models.common.ClusterStateData;
import com.phonepe.drove.models.events.events.DroveClusterMaintenanceModeSetEvent;
import com.phonepe.drove.models.info.nodedata.ExecutorNodeData;
import com.phonepe.drove.models.info.nodedata.NodeTransportType;
import com.phonepe.drove.models.instance.InstanceInfo;
import com.phonepe.drove.models.instance.InstanceState;
import com.phonepe.drove.models.operation.ApplicationOperation;
import com.phonepe.drove.models.operation.TaskOperation;
import com.phonepe.drove.models.operation.ops.ApplicationDestroyOperation;
import com.phonepe.drove.models.operation.taskops.TaskKillOperation;
import com.phonepe.drove.models.taskinstance.TaskInfo;
import io.dropwizard.auth.AuthDynamicFeature;
import io.dropwizard.auth.AuthValueFactoryProvider;
import io.dropwizard.testing.junit5.DropwizardExtensionsSupport;
import io.dropwizard.testing.junit5.ResourceExtension;
import lombok.val;
import org.eclipse.jetty.http.HttpStatus;
import org.glassfish.jersey.server.filter.RolesAllowedDynamicFeature;
import org.glassfish.jersey.test.grizzly.GrizzlyWebTestContainerFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import javax.ws.rs.client.Entity;
import javax.ws.rs.core.GenericType;
import javax.ws.rs.core.MediaType;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.IntStream;

import static com.phonepe.drove.controller.ControllerTestUtils.*;
import static com.phonepe.drove.controller.engine.ValidationResult.failure;
import static com.phonepe.drove.controller.engine.ValidationResult.success;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 *
 */

@ExtendWith(DropwizardExtensionsSupport.class)
class ApisTest {

    private static final ApplicationLifecycleManagementEngine applicationEngine = mock(
            ApplicationLifecycleManagementEngine.class);
    private static final TaskEngine taskEngine = mock(TaskEngine.class);
    private static final ResponseEngine responseEngine = mock(ResponseEngine.class);
    private static final ClusterStateDB clusterStateDB = mock(ClusterStateDB.class);

    private static final ResourceExtension EXT = ResourceExtension.builder()
            .addResource(new Apis(applicationEngine, taskEngine, responseEngine, clusterStateDB))
            .setTestContainerFactory(new GrizzlyWebTestContainerFactory())
            .addProvider(new AuthDynamicFeature(
                    new DummyAuthFilter.Builder()
                            .setAuthenticator(new DummyAuthFilter.DummyAuthenticator())
                            .setAuthorizer(new DroveAuthorizer())
                            .buildAuthFilter()))
            .addProvider(RolesAllowedDynamicFeature.class)
            .addProvider(new AuthValueFactoryProvider.Binder<>(DroveUser.class))
            .build();

    @AfterEach
    void teardown() {
        reset(taskEngine);
        reset(responseEngine);
        reset(clusterStateDB);
    }

    @Test
    void acceptOperation() {

        val maintenance = new AtomicBoolean();
        when(clusterStateDB.currentState())
                .thenAnswer(invocationOnMock
                                    -> maintenance.get()
                                       ? Optional.of(new ClusterStateData(ClusterState.MAINTENANCE, new Date()))
                                       : Optional.empty());
        val success = new AtomicBoolean();
        when(applicationEngine.handleOperation(any(ApplicationOperation.class)))
                .thenAnswer(invocationOnMock ->
                                    success.get()
                                    ? success()
                                    : failure("Test Failure"));
        { // Set maintenance mode .. so request will fail
            maintenance.set(true);
            try (val r = EXT.target("/v1/applications/operations")
                    .request()
                    .post(Entity.entity(new ApplicationDestroyOperation("TEST_APP_1", DEFAULT_CLUSTER_OP),
                                        MediaType.APPLICATION_JSON_TYPE))) {
                assertEquals(HttpStatus.BAD_REQUEST_400, r.getStatus());
            }
        }
        {  //Force a failure by sending null entity
            maintenance.set(false);
            success.set(false);
            try (val r = EXT.target("/v1/applications/operations")
                    .request()
                    .post(Entity.json(null))) {
                assertEquals(HttpStatus.UNPROCESSABLE_ENTITY_422, r.getStatus());
            }
        }

        { //Force a failure empty data
            try (val r = EXT.target("/v1/applications/operations")
                    .request()
                    .post(Entity.json(Map.of()))) {
                assertEquals(HttpStatus.BAD_REQUEST_400, r.getStatus());
            }
        }
        { //Force a app level failure
            try (val r = EXT.target("/v1/operations")
                    .request()
                    .post(Entity.entity(new ApplicationDestroyOperation("TEST_APP_1", DEFAULT_CLUSTER_OP),
                                        MediaType.APPLICATION_JSON_TYPE))) {
                assertEquals(HttpStatus.BAD_REQUEST_400, r.getStatus());
            }
        }
        {
            success.set(true);
            val r = EXT.target("/v1/applications/operations")
                    .request()
                    .post(Entity.entity(new ApplicationDestroyOperation("TEST_APP_1", DEFAULT_CLUSTER_OP),
                                        MediaType.APPLICATION_JSON_TYPE),
                          new GenericType<ApiResponse<Map<String, String>>>() {
                          });
            assertEquals(ApiErrorCode.SUCCESS, r.getStatus());
        }
    }


    @Test
    void cancelJobForCurrentOp() {
        val success = new AtomicBoolean();
        when(applicationEngine.cancelCurrentJob(anyString()))
                .thenAnswer(invocationOnMock -> success.get());
        { // Force a app level failure
            try (val r = EXT.target("/v1/operations/TEST_APP_1/cancel")
                    .request()
                    .post(Entity.json(null))) {
                assertEquals(HttpStatus.BAD_REQUEST_400, r.getStatus());
            }
        }
        {
            success.set(true);
            val r = EXT.target("/v1/operations/TEST_APP_1/cancel")
                    .request()
                    .post(Entity.json(null), new GenericType<ApiResponse<Void>>() {
                    });
            assertEquals(ApiErrorCode.SUCCESS, r.getStatus());
        }
    }

    @Test
    void applications() {
        when(responseEngine.applications(0, Apis.MAX_ELEMENTS)).thenReturn(ApiResponse.success(null));
        when(responseEngine.applications(0, 1024)).thenReturn(ApiResponse.success(null));
        //assertEquals(ApiErrorCode.SUCCESS, apis.applications(1, 1024).getStatus());
        {
            try (val r = EXT.target("/v1/applications")
                    .queryParam("from", -1)
                    .queryParam("size", 1024)
                    .request()
                    .get()) {
                assertEquals(HttpStatus.BAD_REQUEST_400, r.getStatus());
            }
        }
        {
            try (val r = EXT.target("/v1/applications")
                    .queryParam("from", 0)
                    .queryParam("size", Integer.MAX_VALUE)
                    .request()
                    .get()) {
                assertEquals(HttpStatus.BAD_REQUEST_400, r.getStatus());
            }
        }
        {
            val r = EXT.target("/v1/applications")
                    .request()
                    .get(new GenericType<ApiResponse<Map<String, AppSummary>>>() {
                    });
            assertEquals(ApiErrorCode.SUCCESS, r.getStatus());
        }
    }

    @Test
    void application() {
        val spec = appSpec();
        val appSummary = new AppSummary(ControllerUtils.deployableObjectId(spec),
                                        spec.getName(),
                                        10,
                                        10,
                                        100,
                                        512,
                                        spec.getTags(),
                                        ApplicationState.RUNNING,
                                        new Date(),
                                        new Date());
        when(responseEngine.application("TEST_APP_1"))
                .thenReturn(ApiResponse.success(appSummary));
        {
            val r = EXT.target("/v1/applications/TEST_APP_1")
                    .request()
                    .get(new GenericType<ApiResponse<AppSummary>>() {
                    });
            assertEquals(ApiErrorCode.SUCCESS, r.getStatus());
            assertEquals(appSummary, r.getData());
        }
    }

    @Test
    void applicationSpec() {
        val spec = appSpec();

        when(responseEngine.applicationSpec("TEST_APP_1"))
                .thenReturn(ApiResponse.success(spec));
        {
            val r = EXT.target("/v1/applications/TEST_APP_1/spec")
                    .request()
                    .get(new GenericType<ApiResponse<ApplicationSpec>>() {
                    });
            assertEquals(ApiErrorCode.SUCCESS, r.getStatus());
            assertEquals(spec, r.getData());
        }
    }

    @Test
    void applicationInstances() {
        val spec = appSpec();
        val appId = ControllerUtils.deployableObjectId(spec);
        {
            val instances = IntStream.rangeClosed(1, 100)
                    .mapToObj(i -> ControllerTestUtils.generateInstanceInfo(ControllerUtils.deployableObjectId(spec),
                                                                            spec,
                                                                            i))
                    .toList();
            when(responseEngine.applicationInstances(appId, EnumSet.of(InstanceState.HEALTHY)))
                    .thenReturn(ApiResponse.success(instances));

            val r = EXT.target("/v1/applications/" + appId + "/instances")
                    .queryParam("state", InstanceState.HEALTHY.name())
                    .request()
                    .get(new GenericType<ApiResponse<List<InstanceInfo>>>() {
                    });
            assertEquals(ApiErrorCode.SUCCESS, r.getStatus());
            assertEquals(instances, r.getData());
        }
        {
            reset(responseEngine);
            val instances = IntStream.rangeClosed(1, 100)
                    .mapToObj(i -> ControllerTestUtils.generateInstanceInfo(ControllerUtils.deployableObjectId(spec),
                                                                            spec,
                                                                            i))
                    .toList();
            when(responseEngine.applicationInstances(eq(appId), any())).thenReturn(ApiResponse.success(instances));

            val r = EXT.target("/v1/applications/" + appId + "/instances")
                    .request()
                    .get(new GenericType<ApiResponse<List<InstanceInfo>>>() {
                    });
            assertEquals(ApiErrorCode.SUCCESS, r.getStatus());
            assertEquals(instances, r.getData());
        }
    }

    @Test
    void applicationInstance() {
        val spec = appSpec();

        val instance = ControllerTestUtils.generateInstanceInfo(ControllerUtils.deployableObjectId(spec), spec, 1);
        when(responseEngine.instanceDetails("TEST_APP_1", instance.getInstanceId()))
                .thenReturn(ApiResponse.success(instance));
        {
            val r = EXT.target("/v1/applications/TEST_APP_1/instances/" + instance.getInstanceId())
                    .request()
                    .get(new GenericType<ApiResponse<InstanceInfo>>() {
                    });
            assertEquals(ApiErrorCode.SUCCESS, r.getStatus());
            assertEquals(instance, r.getData());
        }
    }

    @Test
    void applicationOldInstances() {
        val spec = appSpec();
        val appId = ControllerUtils.deployableObjectId(spec);

        val instances = IntStream.rangeClosed(1, 100)
                .mapToObj(i -> ControllerTestUtils.generateInstanceInfo(ControllerUtils.deployableObjectId(spec),
                                                                        spec,
                                                                        i))
                .toList();
        when(responseEngine.applicationOldInstances(appId, 0, Apis.MAX_ELEMENTS))
                .thenReturn(ApiResponse.success(instances));
        {
            try (val r = EXT.target("/v1/applications/" + appId + "/instances/old")
                    .queryParam("start", -1)
                    .request()
                    .get()) {
                assertEquals(HttpStatus.BAD_REQUEST_400, r.getStatus());
            }
        }
        {
            try (val r = EXT.target("/v1/applications/" + appId + "/instances/old")
                    .queryParam("size", -1)
                    .request()
                    .get()) {
                assertEquals(HttpStatus.BAD_REQUEST_400, r.getStatus());
            }
        }
        {
            try (val r = EXT.target("/v1/applications/" + appId + "/instances/old")
                    .queryParam("start", Integer.MAX_VALUE)
                    .request()
                    .get()) {
                assertEquals(HttpStatus.BAD_REQUEST_400, r.getStatus());
            }
        }
        {
            try (val r = EXT.target("/v1/applications/" + appId + "/instances/old")
                    .queryParam("size", Integer.MAX_VALUE)
                    .request()
                    .get()) {
                assertEquals(HttpStatus.BAD_REQUEST_400, r.getStatus());
            }
        }

        {
            val r = EXT.target("/v1/applications/" + appId + "/instances/old")
                    .request()
                    .get(new GenericType<ApiResponse<List<InstanceInfo>>>() {
                    });
            assertEquals(ApiErrorCode.SUCCESS, r.getStatus());
            assertEquals(instances, r.getData());
        }
    }

    @Test
    void acceptTaskOperation() {

        val maintenance = new AtomicBoolean();
        when(clusterStateDB.currentState())
                .thenAnswer(invocationOnMock
                                    -> maintenance.get()
                                       ? Optional.of(new ClusterStateData(ClusterState.MAINTENANCE, new Date()))
                                       : Optional.empty());
        val success = new AtomicBoolean();
        when(taskEngine.handleTaskOp(any(TaskOperation.class)))
                .thenAnswer(invocationOnMock ->
                                    success.get()
                                    ? success()
                                    : failure("Test Failure"));
        { // Set maintenance mode .. so request will fail
            maintenance.set(true);
            try (val r = EXT.target("/v1/tasks/operations")
                    .request()
                    .post(Entity.entity(new TaskKillOperation("TEST_APP1", "T001", DEFAULT_CLUSTER_OP),
                                        MediaType.APPLICATION_JSON_TYPE))) {
                assertEquals(HttpStatus.BAD_REQUEST_400, r.getStatus());
            }
        }
        {  //Force a failure by sending null entity
            maintenance.set(false);
            success.set(false);
            try (val r = EXT.target("/v1/tasks/operations")
                    .request()
                    .post(Entity.json(null))) {
                assertEquals(HttpStatus.UNPROCESSABLE_ENTITY_422, r.getStatus());
            }
        }

        { //Force a failure empty data
            try (val r = EXT.target("/v1/tasks/operations")
                    .request()
                    .post(Entity.json(Map.of()))) {
                assertEquals(HttpStatus.BAD_REQUEST_400, r.getStatus());
            }
        }
        { //Force a app level failure
            try (val r = EXT.target("/v1/tasks/operations")
                    .request()
                    .post(Entity.entity(new TaskKillOperation("TEST_APP1", "T001", DEFAULT_CLUSTER_OP),
                                        MediaType.APPLICATION_JSON_TYPE))) {
                assertEquals(HttpStatus.BAD_REQUEST_400, r.getStatus());
            }
        }
        {
            success.set(true);
            val r = EXT.target("/v1/tasks/operations")
                    .request()
                    .post(Entity.entity(new TaskKillOperation("TEST_APP_1", "T001", DEFAULT_CLUSTER_OP),
                                        MediaType.APPLICATION_JSON_TYPE),
                          new GenericType<ApiResponse<Map<String, String>>>() {
                          });
            assertEquals(ApiErrorCode.SUCCESS, r.getStatus());
        }
    }

    @Test
    void tasks() {
        val spec = taskSpec();

        val instance = ControllerTestUtils.generateTaskInfo(spec, 1);
        when(taskEngine.activeTasks()).thenReturn(List.of(instance));
        {
            val r = EXT.target("/v1/tasks")
                    .request()
                    .get(new GenericType<ApiResponse<List<TaskInfo>>>() {
                    });
            assertEquals(ApiErrorCode.SUCCESS, r.getStatus());
            assertEquals(List.of(instance), r.getData());
        }
    }

    @Test
    void taskInstance() {
        val spec = taskSpec();

        val instance = ControllerTestUtils.generateTaskInfo(spec, 1);
        when(responseEngine.taskDetails(spec.getSourceAppName(), spec.getTaskId()))
                .thenReturn(ApiResponse.success(instance));
        {
            val r = EXT.target("/v1/tasks/" + spec.getSourceAppName() + "/instances/" + spec.getTaskId())
                    .request()
                    .get(new GenericType<ApiResponse<TaskInfo>>() {
                    });
            assertEquals(ApiErrorCode.SUCCESS, r.getStatus());
            assertEquals(instance, r.getData());
        }
    }

    @Test
    void deleteTaskInstance() {
        val spec = taskSpec();

        val instance = ControllerTestUtils.generateTaskInfo(spec, 1);
        when(responseEngine.taskDelete(spec.getSourceAppName(), spec.getTaskId()))
                .thenReturn(ApiResponse.success(Map.of("deleted", true)));
        {
            val r = EXT.target("/v1/tasks/" + spec.getSourceAppName() + "/instances/" + spec.getTaskId())
                    .request()
                    .delete(new GenericType<ApiResponse<Map<String, Boolean>>>() {
                    });
            assertEquals(ApiErrorCode.SUCCESS, r.getStatus());
            assertEquals(Map.of("deleted", true), r.getData());
        }
    }

    @Test
    void clusterSummary() {
        val clusterSummary = new ClusterSummary(
                "testhost",
                ClusterState.NORMAL,
                100,
                10,
                5,
                50,
                100,
                1024,
                512_000,
                512_000,
                1024_000);
        when(responseEngine.cluster()).thenReturn(ApiResponse.success(clusterSummary));
        val r = EXT.target("/v1/cluster")
                .request()
                .get(new GenericType<ApiResponse<ClusterSummary>>() {
                });
        assertEquals(ApiErrorCode.SUCCESS, r.getStatus());
        assertEquals(clusterSummary, r.getData());
    }

    @Test
    void nodes() {
        val executors = IntStream.rangeClosed(1, 100)
                .mapToObj(i -> new ExecutorSummary("EX_" + i,
                                                   "host-" + i,
                                                   8080,
                                                   NodeTransportType.HTTP,
                                                   10,
                                                   5,
                                                   512,
                                                   1024,
                                                   Set.of(),
                                                   ExecutorSummary.ExecutorState.ACTIVE))
                .toList();
        when(responseEngine.nodes()).thenReturn(ApiResponse.success(executors));
        val r = EXT.target("/v1/cluster/executors")
                .request()
                .get(new GenericType<ApiResponse<List<ExecutorSummary>>>() {
                });
        assertEquals(ApiErrorCode.SUCCESS, r.getStatus());
        assertEquals(executors, r.getData());
    }

    @Test
    void executorDetails() {
        val executor = generateExecutorNode(1);
        val executorId = executor.getState().getExecutorId();
        when(responseEngine.executorDetails(executorId))
                .thenReturn(ApiResponse.success(executor));
        val r = EXT.target("/v1/cluster/executors/" + executorId)
                .request()
                .get(new GenericType<ApiResponse<ExecutorNodeData>>() {
                });
        assertEquals(ApiErrorCode.SUCCESS, r.getStatus());
        assertEquals(executor, r.getData());
    }

    @Test
    void blacklistExecutor() {
        val executorId = ControllerTestUtils.executorId(1);
        val executorIds = Set.of(executorId);
        when(responseEngine.blacklistExecutors(executorIds))
                .thenReturn(ApiResponse.success(Map.of("successful", executorIds)));
        val r = EXT.target("/v1/cluster/executors/" + executorId + "/blacklist")
                .request()
                .post(Entity.json(null), new GenericType<ApiResponse<Map<String, Set<String>>>>() {
                });
        assertEquals(ApiErrorCode.SUCCESS, r.getStatus());
        assertTrue(r.getData().get("successful").contains(executorId));
    }

    @Test
    void blacklistExecutors() {
        val executorId = ControllerTestUtils.executorId(1);
        val executorIds = Set.of(executorId);
        when(responseEngine.blacklistExecutors(executorIds))
                .thenReturn(ApiResponse.success(Map.of("successful", executorIds)));
        val r = EXT.target("/v1/cluster/executors/blacklist")
                .queryParam("id", executorId)
                .request()
                .post(Entity.json(null), new GenericType<ApiResponse<Map<String, Set<String>>>>() {
                });
        assertEquals(ApiErrorCode.SUCCESS, r.getStatus());
        assertTrue(r.getData().get("successful").contains(executorId));
    }

    @Test
    void unblacklistExecutor() {
        val executorId = ControllerTestUtils.executorId(1);
        when(responseEngine.unblacklistExecutors(Set.of(executorId)))
                .thenReturn(ApiResponse.success(Map.of("successful", Set.of(executorId))));
        val r = EXT.target("/v1/cluster/executors/" + executorId + "/unblacklist")
                .request()
                .post(Entity.json(null), new GenericType<ApiResponse<Map<String, Set<String>>>>() {
                });
        assertEquals(ApiErrorCode.SUCCESS, r.getStatus());
        assertTrue(r.getData().get("successful").contains(executorId));
    }

    @Test
    void unblacklistExecutors() {
        val executorId = ControllerTestUtils.executorId(1);
        when(responseEngine.unblacklistExecutors(Set.of(executorId)))
                .thenReturn(ApiResponse.success(Map.of("successful", Set.of(executorId))));
        val r = EXT.target("/v1/cluster/executors/unblacklist")
                .queryParam("id", executorId)
                .request()
                .post(Entity.json(null), new GenericType<ApiResponse<Map<String, Set<String>>>>() {
                });
        assertEquals(ApiErrorCode.SUCCESS, r.getStatus());
        assertTrue(r.getData().get("successful").contains(executorId));
    }

    @Test
    void setClusterMaintenanceMode() {
        when(responseEngine.setClusterMaintenanceMode()).thenReturn(ApiResponse.success(null));
        val r = EXT.target("/v1/cluster/maintenance/set")
                .request()
                .post(Entity.json(null), new GenericType<ApiResponse<Void>>() {
                });
        assertEquals(ApiErrorCode.SUCCESS, r.getStatus());
    }

    @Test
    void unsetClusterMaintenanceMode() {
        when(responseEngine.unsetClusterMaintenanceMode()).thenReturn(ApiResponse.success(null));
        val r = EXT.target("/v1/cluster/maintenance/unset")
                .request()
                .post(Entity.json(null), new GenericType<ApiResponse<Void>>() {
                });
        assertEquals(ApiErrorCode.SUCCESS, r.getStatus());
    }

    @Test
    void events() {
        when(responseEngine.events(anyLong(), anyInt()))
                .thenReturn(ApiResponse.success(
                        List.of(new DroveClusterMaintenanceModeSetEvent(EventUtils.controllerMetadata()))));
        val r = EXT.target("/v1/cluster/events")
                .request()
                .get(new GenericType<ApiResponse<List<Object>>>() {
                });
        assertEquals(ApiErrorCode.SUCCESS, r.getStatus());
    }

    @Test
    void endpoints() {
        val endpoints = IntStream.rangeClosed(1, 10)
                .mapToObj(i -> new ExposedAppInfo("TEST_APP", "app_id_" + 1, "host-" + i, Map.of(), Set.of()))
                .toList();
        when(responseEngine.endpoints(Set.of())).thenReturn(ApiResponse.success(endpoints));
        val r = EXT.target("/v1/endpoints")
                .request()
                .get(new GenericType<ApiResponse<List<ExposedAppInfo>>>() {
                });
        assertEquals(ApiErrorCode.SUCCESS, r.getStatus());
        assertEquals(endpoints.size(), r.getData().size());
    }

    @Test
    void endpointsForApp() {
        val endpoints = List.of(new ExposedAppInfo("test", "app_id_" + 1, "host-1", Map.of(), Set.of()));
        when(responseEngine.endpoints(Set.of("test"))).thenReturn(ApiResponse.success(endpoints));
        val r = EXT.target("/v1/endpoints/app/test")
                .request()
                .get(new GenericType<ApiResponse<List<ExposedAppInfo>>>() {
                });
        assertEquals(ApiErrorCode.SUCCESS, r.getStatus());
        assertEquals(endpoints.size(), r.getData().size());
    }

    @Test
    void ping() {
        val r = EXT.target("/v1/ping")
                .request()
                .get(new GenericType<ApiResponse<String>>() {
                });
        assertEquals(ApiErrorCode.SUCCESS, r.getStatus());
        assertEquals("pong", r.getData());
    }
}