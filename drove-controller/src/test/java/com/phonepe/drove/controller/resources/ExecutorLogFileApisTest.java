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

import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import com.phonepe.drove.auth.config.ClusterAuthenticationConfig;
import com.phonepe.drove.auth.model.ClusterCommHeaders;
import com.phonepe.drove.controller.ControllerTestUtils;
import com.phonepe.drove.controller.resourcemgmt.ClusterResourcesDB;
import com.phonepe.drove.controller.statedb.ApplicationInstanceInfoDB;
import com.phonepe.drove.controller.statedb.LocalServiceStateDB;
import com.phonepe.drove.controller.statedb.TaskDB;
import com.phonepe.drove.controller.utils.ControllerUtils;
import com.phonepe.drove.models.info.nodedata.NodeType;
import com.phonepe.drove.models.taskinstance.TaskState;
import io.dropwizard.testing.junit5.DropwizardExtensionsSupport;
import io.dropwizard.testing.junit5.ResourceExtension;
import lombok.SneakyThrows;
import lombok.val;
import org.apache.commons.io.IOUtils;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.eclipse.jetty.http.HttpStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;

import javax.ws.rs.core.GenericType;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.phonepe.drove.controller.ControllerTestUtils.EXECUTOR_ID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 *
 */
@WireMockTest
@ExtendWith(DropwizardExtensionsSupport.class)
class ExecutorLogFileApisTest {
    private static final ApplicationInstanceInfoDB instanceInfoDB = mock(ApplicationInstanceInfoDB.class);
    private static final TaskDB taskDB = mock(TaskDB.class);
    private static final LocalServiceStateDB localServiceDB = mock(LocalServiceStateDB.class);
    private static final ClusterResourcesDB clusterResourcesDB = mock(ClusterResourcesDB.class);
    private static final ClusterAuthenticationConfig config = ClusterAuthenticationConfig.builder()
            .secrets(List.of(new ClusterAuthenticationConfig.SecretConfig(NodeType.CONTROLLER, "SuperSecret")))
            .build();
    private static final CloseableHttpClient httpClient = HttpClients.createDefault();

    private static final ResourceExtension EXT = ResourceExtension.builder()
            .addResource(new ExecutorLogFileApis(instanceInfoDB, taskDB, localServiceDB, clusterResourcesDB, config, httpClient))
            .build();

    @AfterEach
    void reset() {
        Mockito.reset(instanceInfoDB, taskDB, localServiceDB, clusterResourcesDB);
    }

    @Test
    void testLogsListing(final WireMockRuntimeInfo executor) {
        val appSpec = ControllerTestUtils.appSpec();
        val appName = appSpec.getName();
        val taskSpec = ControllerTestUtils.taskSpec(appName, 1);
        val appId = ControllerUtils.deployableObjectId(appSpec);
        val taskInfo = ControllerTestUtils.generateTaskInfo(
                taskSpec, 0, TaskState.RUNNING, null, null);
        val instanceInfo = ControllerTestUtils.generateInstanceInfo(appId, appSpec, 1);
        val executorHostInfo = ControllerTestUtils.executorHost(
                "localhost",
                1,
                executor.getHttpPort(),
                List.of(instanceInfo),
                List.of(taskInfo),
                false);
        when(clusterResourcesDB.lastKnownSnapshot(EXECUTOR_ID)).thenReturn(Optional.of(executorHostInfo));
        when(clusterResourcesDB.currentSnapshot(EXECUTOR_ID)).thenReturn(Optional.of(executorHostInfo));


        { //App testing
            when(instanceInfoDB.instance(appId, instanceInfo.getInstanceId()))
                    .thenReturn(Optional.of(instanceInfo));
            val executorApi = String.format("/apis/v1/logs/filestream/%s/%s/list", appId, instanceInfo.getInstanceId());
            stubFor(get(executorApi)
                            .withHeader(ClusterCommHeaders.CLUSTER_AUTHORIZATION, equalTo("SuperSecret"))
                            .willReturn(jsonResponse(Map.of("files", List.of("output.log")), HttpStatus.OK_200)));
            val api = String.format("/v1/logfiles/applications/%s/%s/list", appId, instanceInfo.getInstanceId());
            val r = EXT.target(api)
                    .request()
                    .get(new GenericType<Map<String, List<String>>>() {
                    });
            assertTrue(r.containsKey("files"));
            assertEquals(1, r.get("files").size());
        }
        { //Task testing
            when(taskDB.task(appName, taskInfo.getTaskId())).thenReturn(Optional.of(taskInfo));
            val executorApi = String.format("/apis/v1/logs/filestream/%s/%s/list", appName, taskInfo.getTaskId());
            stubFor(get(executorApi)
                            .withHeader(ClusterCommHeaders.CLUSTER_AUTHORIZATION, equalTo("SuperSecret"))
                            .willReturn(jsonResponse(Map.of("files", List.of("output.log")), HttpStatus.OK_200)));
            val api = String.format("/v1/logfiles/tasks/%s/%s/list", appName, taskInfo.getTaskId());
            val r = EXT.target(api)
                    .request()
                    .get(new GenericType<Map<String, List<String>>>() {
                    });
            assertTrue(r.containsKey("files"));
            assertEquals(1, r.get("files").size());
        }
    }


    @Test
    void testLogfileRead(final WireMockRuntimeInfo executor) {
        val appSpec = ControllerTestUtils.appSpec();
        val appName = appSpec.getName();
        val taskSpec = ControllerTestUtils.taskSpec(appName, 1);
        val appId = ControllerUtils.deployableObjectId(appSpec);
        val taskInfo = ControllerTestUtils.generateTaskInfo(
                taskSpec, 0, TaskState.RUNNING, null, null);
        val instanceInfo = ControllerTestUtils.generateInstanceInfo(appId, appSpec, 1);
        val executorHostInfo = ControllerTestUtils.executorHost(
                "localhost",
                1,
                executor.getHttpPort(),
                List.of(instanceInfo),
                List.of(taskInfo),
                false);
        when(clusterResourcesDB.lastKnownSnapshot(EXECUTOR_ID)).thenReturn(Optional.of(executorHostInfo));
        when(clusterResourcesDB.currentSnapshot(EXECUTOR_ID)).thenReturn(Optional.of(executorHostInfo));


        { //App testing
            when(instanceInfoDB.instance(appId, instanceInfo.getInstanceId()))
                    .thenReturn(Optional.of(instanceInfo));
            val executorApi = String.format("/apis/v1/logs/filestream/%s/%s/read/output.log",
                                            appId,
                                            instanceInfo.getInstanceId());
            stubFor(get(urlPathEqualTo(executorApi))
                            .withHeader(ClusterCommHeaders.CLUSTER_AUTHORIZATION, equalTo("SuperSecret"))
                            .willReturn(jsonResponse(Map.of("data", "TestData", "offset", 100), HttpStatus.OK_200)));
            val api = String.format("/v1/logfiles/applications/%s/%s/read/output.log",
                                    appId,
                                    instanceInfo.getInstanceId());
            val r = EXT.target(api)
                    .request()
                    .get(new GenericType<Map<String, Object>>() {
                    });
            assertTrue(r.containsKey("data"));
            assertEquals("TestData", Objects.toString(r.get("data")));
            assertTrue(r.containsKey("offset"));
            assertEquals(100, r.get("offset"));
        }
        { //Task testing
            when(taskDB.task(appName, taskInfo.getTaskId())).thenReturn(Optional.of(taskInfo));
            val executorApi = String.format("/apis/v1/logs/filestream/%s/%s/read/output.log",
                                            appName,
                                            taskInfo.getTaskId());
            stubFor(get(urlPathEqualTo(executorApi))
                            .withHeader(ClusterCommHeaders.CLUSTER_AUTHORIZATION, equalTo("SuperSecret"))
                            .willReturn(jsonResponse(Map.of("data", "TestData", "offset", 100), HttpStatus.OK_200)));
            val api = String.format("/v1/logfiles/tasks/%s/%s/read/output.log", appName, taskInfo.getTaskId());
            val r = EXT.target(api)
                    .request()
                    .get(new GenericType<Map<String, Object>>() {
                    });
            assertTrue(r.containsKey("data"));
            assertEquals("TestData", Objects.toString(r.get("data")));
            assertTrue(r.containsKey("offset"));
            assertEquals(100, r.get("offset"));
        }
    }

    @Test
    @SneakyThrows
    void testLogfileDownload(final WireMockRuntimeInfo executor) {
        val appSpec = ControllerTestUtils.appSpec();
        val appName = appSpec.getName();
        val taskSpec = ControllerTestUtils.taskSpec(appName, 1);
        val appId = ControllerUtils.deployableObjectId(appSpec);
        val taskInfo = ControllerTestUtils.generateTaskInfo(
                taskSpec, 0, TaskState.RUNNING, null, null);
        val instanceInfo = ControllerTestUtils.generateInstanceInfo(appId, appSpec, 1);
        val executorHostInfo = ControllerTestUtils.executorHost(
                "localhost",
                1,
                executor.getHttpPort(),
                List.of(instanceInfo),
                List.of(taskInfo),
                false);
        when(clusterResourcesDB.lastKnownSnapshot(EXECUTOR_ID)).thenReturn(Optional.of(executorHostInfo));
        when(clusterResourcesDB.currentSnapshot(EXECUTOR_ID)).thenReturn(Optional.of(executorHostInfo));


        { //App testing
            when(instanceInfoDB.instance(appId, instanceInfo.getInstanceId()))
                    .thenReturn(Optional.of(instanceInfo));
            val executorApi = String.format("/apis/v1/logs/filestream/%s/%s/download/output.log",
                                            appId,
                                            instanceInfo.getInstanceId());
            stubFor(get(urlPathEqualTo(executorApi))
                            .withHeader(ClusterCommHeaders.CLUSTER_AUTHORIZATION, equalTo("SuperSecret"))
                            .willReturn(ResponseDefinitionBuilder.responseDefinition()
                                                .withStatus(HttpStatus.OK_200)
                                                .withBodyFile("output.log")));
            val api = String.format("/v1/logfiles/applications/%s/%s/download/output.log",
                                    appId,
                                    instanceInfo.getInstanceId());
            try(val r = EXT.target(api)
                    .request()
                    .get()) {
                val tmpFile = Files.createTempFile("dt", "dld");
                try(val out = new FileOutputStream(tmpFile.toFile())) {
                    IOUtils.copy(r.readEntity(InputStream.class), out);
                }
                assertEquals("This is sample log file", Files.readString(tmpFile));
            }
        }
        { //Task testing
            when(taskDB.task(appName, taskInfo.getTaskId())).thenReturn(Optional.of(taskInfo));
            val executorApi = String.format("/apis/v1/logs/filestream/%s/%s/download/output.log",
                                            appName,
                                            taskInfo.getTaskId());
            stubFor(get(urlPathEqualTo(executorApi))
                            .withHeader(ClusterCommHeaders.CLUSTER_AUTHORIZATION, equalTo("SuperSecret"))
                            .willReturn(ResponseDefinitionBuilder.responseDefinition()
                                                .withStatus(HttpStatus.OK_200)
                                                .withBodyFile("output.log")));
            val api = String.format("/v1/logfiles/tasks/%s/%s/download/output.log",
                                    appName,
                                    taskInfo.getTaskId());
            try(val r = EXT.target(api)
                    .request()
                    .get()) {
                System.out.println(r);
                val tmpFile = Files.createTempFile("dt", "dld");
                try(val out = new FileOutputStream(tmpFile.toFile())) {
                    IOUtils.copy(r.readEntity(InputStream.class), out);
                }
                assertEquals("This is sample log file", Files.readString(tmpFile));
            }
        }
    }

    @Test
    void testFailNoNode() {
        val appSpec = ControllerTestUtils.appSpec();
        val appName = appSpec.getName();
        val taskSpec = ControllerTestUtils.taskSpec(appName, 1);
        val appId = ControllerUtils.deployableObjectId(appSpec);
        val taskInfo = ControllerTestUtils.generateTaskInfo(
                taskSpec, 0, TaskState.RUNNING, null, null);
        val instanceInfo = ControllerTestUtils.generateInstanceInfo(appId, appSpec, 1);
        when(clusterResourcesDB.lastKnownSnapshot(EXECUTOR_ID)).thenReturn(Optional.empty());
        when(clusterResourcesDB.currentSnapshot(EXECUTOR_ID)).thenReturn(Optional.empty());


        { //App testing
            when(instanceInfoDB.instance(appId, instanceInfo.getInstanceId()))
                    .thenReturn(Optional.of(instanceInfo));
            val apiPrefix = String.format("/v1/logfiles/applications/%s/%s", appId, instanceInfo.getInstanceId());
            assertNull(EXT.target(apiPrefix + "/list")
                               .request()
                               .get(new GenericType<Map<String, List<String>>>() {
                               }));
            assertNull(EXT.target(apiPrefix + "/read/output.log")
                               .request()
                               .get(new GenericType<Map<String, List<String>>>() {
                               }));
            assertNull(EXT.target(apiPrefix + "/download/output.log")
                               .request()
                               .get(new GenericType<Map<String, List<String>>>() {
                               }));
        }
        { //Task testing
            when(taskDB.task(appName, taskInfo.getTaskId())).thenReturn(Optional.of(taskInfo));
            val apiPrefix = String.format("/v1/logfiles/tasks/%s/%s", appName, taskInfo.getTaskId());
            assertNull(EXT.target(apiPrefix + "/list")
                               .request()
                               .get(new GenericType<Map<String, List<String>>>() {
                               }));
            assertNull(EXT.target(apiPrefix + "/read/output.log")
                               .request()
                               .get(new GenericType<Map<String, List<String>>>() {
                               }));
            assertNull(EXT.target(apiPrefix + "/download/output.log")
                               .request()
                               .get(new GenericType<Map<String, List<String>>>() {
                               }));
        }
    }

    @Test
    void testFailNoInstanceInfo() {
        val appSpec = ControllerTestUtils.appSpec();
        val appName = appSpec.getName();
        val taskSpec = ControllerTestUtils.taskSpec(appName, 1);
        val taskInfo = ControllerTestUtils.generateTaskInfo(
                taskSpec, 0, TaskState.RUNNING, null, null);
        when(clusterResourcesDB.lastKnownSnapshot(EXECUTOR_ID)).thenReturn(Optional.empty());
        when(clusterResourcesDB.currentSnapshot(EXECUTOR_ID)).thenReturn(Optional.empty());
        when(instanceInfoDB.instance(anyString(), anyString())).thenReturn(Optional.empty());
        when(taskDB.task(anyString(), anyString())).thenReturn(Optional.of(taskInfo));

        { //App testing
            assertNull(EXT.target("/v1/logfiles/applications/wrong_app_id/wrong_instance_id/list")
                               .request()
                               .get(new GenericType<Map<String, List<String>>>() {
                               }));
            assertNull(EXT.target("/v1/logfiles/applications/wrong_app_id/wrong_instance_id/read/output.log")
                               .request()
                               .get(new GenericType<Map<String, List<String>>>() {
                               }));
            assertNull(EXT.target("/v1/logfiles/applications/wrong_app_id/wrong_instance_id/download/output.log")
                               .request()
                               .get(new GenericType<Map<String, List<String>>>() {
                               }));
        }
        { //Task testing
            assertNull(EXT.target("/v1/logfiles/tasks/wrong_app_name/wrong_instance_id/list")
                               .request()
                               .get(new GenericType<Map<String, List<String>>>() {
                               }));
            assertNull(EXT.target("/v1/logfiles/tasks/wrong_app_name/wrong_instance_id/read/output.log")
                               .request()
                               .get(new GenericType<Map<String, List<String>>>() {
                               }));
            assertNull(EXT.target("/v1/logfiles/tasks/wrong_app_name/wrong_instance_id/download/output.log")
                               .request()
                               .get(new GenericType<Map<String, List<String>>>() {
                               }));
        }
    }

    @Test
    @SneakyThrows
    void testFailExecutorNon200(final WireMockRuntimeInfo executor) {
        val appSpec = ControllerTestUtils.appSpec();
        val appName = appSpec.getName();
        val taskSpec = ControllerTestUtils.taskSpec(appName, 1);
        val appId = ControllerUtils.deployableObjectId(appSpec);
        val taskInfo = ControllerTestUtils.generateTaskInfo(
                taskSpec, 0, TaskState.RUNNING, null, null);
        val instanceInfo = ControllerTestUtils.generateInstanceInfo(appId, appSpec, 1);
        val executorHostInfo = ControllerTestUtils.executorHost(
                "localhost",
                1,
                executor.getHttpPort(),
                List.of(instanceInfo),
                List.of(taskInfo),
                false);
        when(clusterResourcesDB.lastKnownSnapshot(EXECUTOR_ID)).thenReturn(Optional.of(executorHostInfo));
        when(clusterResourcesDB.currentSnapshot(EXECUTOR_ID)).thenReturn(Optional.of(executorHostInfo));


        { //App testing
            when(instanceInfoDB.instance(appId, instanceInfo.getInstanceId()))
                    .thenReturn(Optional.of(instanceInfo));
            val executorApi = String.format("/apis/v1/logs/filestream/%s/%s/list",
                                            appId,
                                            instanceInfo.getInstanceId());
            stubFor(get(urlPathEqualTo(executorApi))
                            .withHeader(ClusterCommHeaders.CLUSTER_AUTHORIZATION, equalTo("SuperSecret"))
                            .willReturn(serverError().withBody("test error")));
            val api = String.format("/v1/logfiles/applications/%s/%s/list",
                                    appId,
                                    instanceInfo.getInstanceId());
            try(val r = EXT.target(api)
                    .request()
                    .get()) {
                assertEquals(500, r.getStatus());
                val err = r.readEntity(new GenericType<Map<String, String>>() {});
                assertTrue(err.containsKey("error"));
                assertEquals("Executor call returned: 500 body: test error", err.get("error"));            }
        }
        { //Task testing
            when(taskDB.task(appName, taskInfo.getTaskId())).thenReturn(Optional.of(taskInfo));
            val executorApi = String.format("/apis/v1/logs/filestream/%s/%s/list",
                                            appName,
                                            taskInfo.getTaskId());
            stubFor(get(urlPathEqualTo(executorApi))
                            .withHeader(ClusterCommHeaders.CLUSTER_AUTHORIZATION, equalTo("SuperSecret"))
                            .willReturn(serverError().withBody("test error")));
            val api = String.format("/v1/logfiles/tasks/%s/%s/list",
                                    appName,
                                    taskInfo.getTaskId());
            try(val r = EXT.target(api)
                    .request()
                    .get()) {
                assertEquals(500, r.getStatus());
                val err = r.readEntity(new GenericType<Map<String, String>>() {});
                assertTrue(err.containsKey("error"));
                assertEquals("Executor call returned: 500 body: test error", err.get("error"));
            }
        }
    }
}