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

package com.phonepe.drove.executor.resources;

import com.google.common.base.Strings;
import com.phonepe.drove.auth.config.ClusterAuthenticationConfig;
import com.phonepe.drove.auth.core.DroveAuthorizer;
import com.phonepe.drove.auth.core.DroveClusterSecretAuthenticator;
import com.phonepe.drove.auth.core.DroveUnauthorizedHandler;
import com.phonepe.drove.auth.filters.DroveClusterAuthFilter;
import com.phonepe.drove.auth.model.ClusterCommHeaders;
import com.phonepe.drove.auth.model.DroveUser;
import com.phonepe.drove.executor.logging.LogInfo;
import com.phonepe.drove.models.info.nodedata.NodeType;
import io.dropwizard.auth.AuthDynamicFeature;
import io.dropwizard.auth.AuthValueFactoryProvider;
import io.dropwizard.testing.junit5.DropwizardExtensionsSupport;
import io.dropwizard.testing.junit5.ResourceExtension;
import lombok.SneakyThrows;
import lombok.val;
import org.apache.commons.io.IOUtils;
import org.eclipse.jetty.http.HttpStatus;
import org.glassfish.jersey.server.filter.RolesAllowedDynamicFeature;
import org.glassfish.jersey.test.grizzly.GrizzlyWebTestContainerFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import javax.ws.rs.core.GenericType;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;

import static com.phonepe.drove.auth.core.AuthConstants.NODE_ID_HEADER;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 *
 */
@ExtendWith(DropwizardExtensionsSupport.class)
class LogFileStreamTest {
    private static final LogInfo logInfo = mock(LogInfo.class);

    private static final ResourceExtension EXT = ResourceExtension.builder()
            .setTestContainerFactory(new GrizzlyWebTestContainerFactory())
            .addResource(new LogFileStream(logInfo))
            .addProvider(new AuthDynamicFeature(new DroveClusterAuthFilter.Builder()
                                                        .setAuthenticator(new DroveClusterSecretAuthenticator(
                                                                ClusterAuthenticationConfig.DEFAULT))
                                                        .setAuthorizer(new DroveAuthorizer())
                                                        .setUnauthorizedHandler(new DroveUnauthorizedHandler())
                                                        .buildAuthFilter()))
            .addProvider(RolesAllowedDynamicFeature.class)
            .addProvider(new AuthValueFactoryProvider.Binder<>(DroveUser.class))
            .build();

    @AfterEach
    void resetMocks() {
        reset(logInfo);
    }

    @Test
    void testNoDroveLogging() {
        when(logInfo.isDroveLogging()).thenReturn(false);
        { //Test Listing
            val files = EXT.target("/v1/logs/filestream/test_app/test_instance/list")
                    .request()
                    .header(NODE_ID_HEADER, NodeType.CONTROLLER.name())
                    .header(ClusterCommHeaders.CLUSTER_AUTHORIZATION, "DefaultControllerSecret")
                    .get(new GenericType<Map<String, List<String>>>() {
                    });
            assertTrue(files.containsKey("files"));
            assertTrue(files.get("files").isEmpty());
        }
        { //Read
            try (val r = EXT.target("/v1/logs/filestream/test_app/test_instance/read/output.1.log")
                    .request()
                    .header(NODE_ID_HEADER, NodeType.CONTROLLER.name())
                    .header(ClusterCommHeaders.CLUSTER_AUTHORIZATION, "DefaultControllerSecret")
                    .get()) {
                assertEquals(HttpStatus.BAD_REQUEST_400, r.getStatus());
                val files = r.readEntity(new GenericType<Map<String, String>>() {
                });
                assertFalse(files.containsKey("files"));
                assertTrue(files.containsKey("error"));
                assertEquals("This only works if the 'drove' appender type is configured",
                             files.get("error"));
            }
        }
        { //Download
            try (val r = EXT.target("/v1/logs/filestream/test_app/test_instance/download/output.1.log")
                    .request()
                    .header(NODE_ID_HEADER, NodeType.CONTROLLER.name())
                    .header(ClusterCommHeaders.CLUSTER_AUTHORIZATION, "DefaultControllerSecret")
                    .get()) {
                assertEquals(HttpStatus.BAD_REQUEST_400, r.getStatus());
                val files = r.readEntity(new GenericType<Map<String, String>>() {
                });
                assertTrue(files.containsKey("error"));
                assertEquals("This only works if the 'drove' appender type is configured",
                             files.get("error"));
            }
        }
    }

    @Test
    @SneakyThrows
    void testFileList() {
        when(logInfo.isDroveLogging()).thenReturn(true);
        when(logInfo.logPathFor(anyString(), anyString()))
                .thenReturn("src/test/resources/logtest/test_app/test_instance");
        when(logInfo.getLogPrefix()).thenReturn(new File("src/test/resources/logtest").getCanonicalPath());
        { //Test Listing
            val files = EXT.target("/v1/logs/filestream/test_app/test_instance/list")
                    .request()
                    .header(NODE_ID_HEADER, NodeType.CONTROLLER.name())
                    .header(ClusterCommHeaders.CLUSTER_AUTHORIZATION, "DefaultControllerSecret")
                    .get(new GenericType<Map<String, List<String>>>() {
                    });
            assertTrue(files.containsKey("files"));
            assertFalse(files.get("files").isEmpty());
            assertEquals(2, files.get("files").size());
        }
    }

    @Test
    @SneakyThrows
    void testFileListException() {
        when(logInfo.isDroveLogging()).thenReturn(true);
        when(logInfo.logPathFor(anyString(), anyString()))
                .thenReturn("wrong-path");
        when(logInfo.getLogPrefix()).thenReturn(new File("").getCanonicalPath());
        { //Test Listing
            try (val r = EXT.target("/v1/logs/filestream/test_app/test_instance/list")
                    .request()
                    .header(NODE_ID_HEADER, NodeType.CONTROLLER.name())
                    .header(ClusterCommHeaders.CLUSTER_AUTHORIZATION, "DefaultControllerSecret")
                    .get()) {
                assertEquals(HttpStatus.BAD_REQUEST_400, r.getStatus());
                val files = r.readEntity(new GenericType<Map<String, String>>() {
                });
                assertTrue(files.containsKey("error"));
                assertTrue(files.get("error").startsWith("Could not read logs from wrong-path"));
            }
        }
    }

    @Test
    @SneakyThrows
    void testFileRead() {
        when(logInfo.isDroveLogging()).thenReturn(true);
        when(logInfo.logPathFor(anyString(), anyString()))
                .thenReturn("src/test/resources/logtest/test_app/test_instance");
        when(logInfo.getLogPrefix()).thenReturn(new File("src/test/resources/logtest").getCanonicalPath());
        { //On first call, it sends the current last offset only
            val logLines = EXT.target("/v1/logs/filestream/test_app/test_instance/read/output.1.log")
                    .request()
                    .header(NODE_ID_HEADER, NodeType.CONTROLLER.name())
                    .header(ClusterCommHeaders.CLUSTER_AUTHORIZATION, "DefaultControllerSecret")
                    .get(new GenericType<LogFileStream.LogBuffer>() {
                    });
            assertEquals(14, logLines.getOffset());
            assertTrue(Strings.isNullOrEmpty(logLines.getData()));
        }

        { //If only offset is sent it will read 32_768
            val logLines = EXT.target("/v1/logs/filestream/test_app/test_instance/read/output.1.log")
                    .queryParam("offset", "0")
                    .request()
                    .header(NODE_ID_HEADER, NodeType.CONTROLLER.name())
                    .header(ClusterCommHeaders.CLUSTER_AUTHORIZATION, "DefaultControllerSecret")
                    .get(new GenericType<LogFileStream.LogBuffer>() {
                    });
            assertEquals(0, logLines.getOffset());
            assertEquals("First log file", logLines.getData());
        }

        { //If offset and length are sent it will read only given size
            val logLines = EXT.target("/v1/logs/filestream/test_app/test_instance/read/output.1.log")
                    .queryParam("offset", "0")
                    .queryParam("length", "10")
                    .request()
                    .header(NODE_ID_HEADER, NodeType.CONTROLLER.name())
                    .header(ClusterCommHeaders.CLUSTER_AUTHORIZATION, "DefaultControllerSecret")
                    .get(new GenericType<LogFileStream.LogBuffer>() {
                    });
            assertEquals(0, logLines.getOffset());
            assertEquals("First log ", logLines.getData());
        }
        { //To read next content, pass the previous received data size as offset
            val logLines = EXT.target("/v1/logs/filestream/test_app/test_instance/read/output.1.log")
                    .queryParam("offset", "14")
                    .queryParam("length", "10")
                    .request()
                    .header(NODE_ID_HEADER, NodeType.CONTROLLER.name())
                    .header(ClusterCommHeaders.CLUSTER_AUTHORIZATION, "DefaultControllerSecret")
                    .get(new GenericType<LogFileStream.LogBuffer>() {
                    });
            assertEquals(14, logLines.getOffset());
            assertTrue(Strings.isNullOrEmpty(logLines.getData()));
        }
        { //For scrolling, the last datasize needs to be sent.
            val logLines = EXT.target("/v1/logs/filestream/test_app/test_instance/read/output.1.log")
                    .queryParam("offset", "10")
                    .queryParam("length", "10")
                    .request()
                    .header(NODE_ID_HEADER, NodeType.CONTROLLER.name())
                    .header(ClusterCommHeaders.CLUSTER_AUTHORIZATION, "DefaultControllerSecret")
                    .get(new GenericType<LogFileStream.LogBuffer>() {
                    });
            assertEquals(10, logLines.getOffset());
            assertEquals("file", logLines.getData());
        }
        { //Length without offset is bad request
            try (val r = EXT.target("/v1/logs/filestream/test_app/test_instance/read/output.1.log")
                    .queryParam("length", "10")
                    .request()
                    .header(NODE_ID_HEADER, NodeType.CONTROLLER.name())
                    .header(ClusterCommHeaders.CLUSTER_AUTHORIZATION, "DefaultControllerSecret")
                    .get()) {
                assertEquals(HttpStatus.BAD_REQUEST_400, r.getStatus());
                val files = r.readEntity(new GenericType<Map<String, String>>() {
                });
                assertTrue(files.containsKey("error"));
                assertEquals(
                        "Error reading file src/test/resources/logtest/test_app/test_instance/output.1.log: Negative seek offset",
                        files.get("error"));
            }
        }
        { //Invalid filenames means bad request
            try (val r = EXT.target("/v1/logs/filestream/test_app/test_instance/read/output.wrong.log")
                    .request()
                    .header(NODE_ID_HEADER, NodeType.CONTROLLER.name())
                    .header(ClusterCommHeaders.CLUSTER_AUTHORIZATION, "DefaultControllerSecret")
                    .get()) {
                assertEquals(HttpStatus.BAD_REQUEST_400, r.getStatus());
                val files = r.readEntity(new GenericType<Map<String, String>>() {
                });
                assertTrue(files.containsKey("error"));
                assertEquals(
                        "Could not read log file: src/test/resources/logtest/test_app/test_instance/output.wrong.log",
                        files.get("error"));
            }
        }
    }

    @Test
    @SneakyThrows
    void testFileDownload() {
        when(logInfo.isDroveLogging()).thenReturn(true);
        when(logInfo.logPathFor(anyString(), anyString()))
                .thenReturn("src/test/resources/logtest/test_app/test_instance");
        when(logInfo.getLogPrefix()).thenReturn(new File("src/test/resources/logtest").getCanonicalPath());
        try (val r = EXT.target("/v1/logs/filestream/test_app/test_instance/download/output.1.log")
                .request()
                .header(NODE_ID_HEADER, NodeType.CONTROLLER.name())
                .header(ClusterCommHeaders.CLUSTER_AUTHORIZATION, "DefaultControllerSecret")
                .get()) {
            val tmpFile = Files.createTempFile("dt", "dld");
            try (val out = new FileOutputStream(tmpFile.toFile())) {
                IOUtils.copy(r.readEntity(InputStream.class), out);
            }
            assertEquals("First log file", Files.readString(tmpFile));
        }
        { //Invalid filenames means bad request
            try (val r = EXT.target("/v1/logs/filestream/test_app/test_instance/download/output.wrong.log")
                    .request()
                    .header(NODE_ID_HEADER, NodeType.CONTROLLER.name())
                    .header(ClusterCommHeaders.CLUSTER_AUTHORIZATION, "DefaultControllerSecret")
                    .get()) {
                assertEquals(HttpStatus.BAD_REQUEST_400, r.getStatus());
                val files = r.readEntity(new GenericType<Map<String, String>>() {
                });
                assertTrue(files.containsKey("error"));
                assertEquals(
                        "Could not read log file: src/test/resources/logtest/test_app/test_instance/output.wrong.log",
                        files.get("error"));
            }
        }
    }

    @Test
    @SneakyThrows
    void testPathBoundCheck() {
        when(logInfo.isDroveLogging()).thenReturn(true);
        when(logInfo.logPathFor(anyString(), anyString()))
                .thenReturn("src/test/resources/logtest/test_app/test_instance");
        when(logInfo.getLogPrefix()).thenReturn("/drove");
        { //Invalid filenames means bad request
            try (val r = EXT.target("/v1/logs/filestream/test_app/test_instance/read/output.1.log")
                    .queryParam("offset", "0")
                    .request()
                    .header(NODE_ID_HEADER, NodeType.CONTROLLER.name())
                    .header(ClusterCommHeaders.CLUSTER_AUTHORIZATION, "DefaultControllerSecret")
                    .get()) {
                assertEquals(HttpStatus.BAD_REQUEST_400, r.getStatus());
                val files = r.readEntity(new GenericType<Map<String, String>>() {
                });
                assertTrue(files.containsKey("error"));
                assertTrue(files.get("error").startsWith("Log read request to out of bounds directory"));
            }
        }
    }

}