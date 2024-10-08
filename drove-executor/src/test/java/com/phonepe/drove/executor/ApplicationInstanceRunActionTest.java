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

package com.phonepe.drove.executor;

import com.codahale.metrics.SharedMetricRegistries;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.phonepe.drove.common.AbstractTestBase;
import com.phonepe.drove.common.CommonTestUtils;
import com.phonepe.drove.common.CommonUtils;
import com.phonepe.drove.common.model.ApplicationInstanceSpec;
import com.phonepe.drove.executor.model.ExecutorInstanceInfo;
import com.phonepe.drove.executor.resourcemgmt.OverProvisioning;
import com.phonepe.drove.executor.resourcemgmt.ResourceConfig;
import com.phonepe.drove.executor.resourcemgmt.ResourceInfo;
import com.phonepe.drove.executor.resourcemgmt.ResourceManager;
import com.phonepe.drove.executor.statemachine.InstanceActionContext;
import com.phonepe.drove.executor.statemachine.application.actions.ApplicationExecutableFetchAction;
import com.phonepe.drove.executor.statemachine.application.actions.ApplicationInstanceRunAction;
import com.phonepe.drove.executor.utils.DockerUtils;
import com.phonepe.drove.models.application.PortSpec;
import com.phonepe.drove.models.application.PortType;
import com.phonepe.drove.models.application.executable.DockerCoordinates;
import com.phonepe.drove.models.application.logging.LocalLoggingSpec;
import com.phonepe.drove.models.common.HTTPCallSpec;
import com.phonepe.drove.models.common.Protocol;
import com.phonepe.drove.models.config.impl.ExecutorHttpFetchConfigSpec;
import com.phonepe.drove.models.config.impl.ExecutorLocalFileConfigSpec;
import com.phonepe.drove.models.config.impl.InlineConfigSpec;
import com.phonepe.drove.models.info.resources.PhysicalLayout;
import com.phonepe.drove.models.info.resources.allocation.CPUAllocation;
import com.phonepe.drove.models.info.resources.allocation.MemoryAllocation;
import com.phonepe.drove.models.instance.InstanceState;
import com.phonepe.drove.models.instance.LocalInstanceInfo;
import com.phonepe.drove.statemachine.StateData;
import io.dropwizard.util.Duration;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static com.github.tomakehurst.wiremock.client.WireMock.ok;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static com.phonepe.drove.common.CommonTestUtils.APP_IMAGE_NAME;
import static com.phonepe.drove.executor.ExecutorTestingUtils.DOCKER_CLIENT;
import static com.phonepe.drove.executor.ExecutorTestingUtils.runCmd;
import static com.phonepe.drove.models.instance.InstanceState.PROVISIONING;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 *
 */
@Slf4j
class ApplicationInstanceRunActionTest extends AbstractTestBase {

    @RegisterExtension
    static WireMockExtension configServer = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort())
            .build();

    @Test
    @SneakyThrows
    void testRun() {
        runAction(1, new ResourceConfig(), true);
    }

    @Test
    @SneakyThrows
    void testRunWithOverProvisioning() {
        val availableCPUs = Runtime.getRuntime().availableProcessors();
        runAction(4, new ResourceConfig()
                .setOverProvisioning(new OverProvisioning()
                                             .setEnabled(true)),
                  true);
    }


    @Test
    @SneakyThrows
    void testRunWithOverProvisioningNoCpuset() {
        val availableCPUs = Runtime.getRuntime().availableProcessors();
        runAction(Math.max(2, availableCPUs + 2), new ResourceConfig()
                .setOverProvisioning(new OverProvisioning()
                                             .setCpuMultiplier(10)
                                             .setMemoryMultiplier(10)
                                             .setEnabled(true)),
                  false); //Will have all CPUs as cpuset will not be done
    }

    @SneakyThrows
    void runAction(int numCpus, final ResourceConfig resourceConfig, boolean exactMatch) {
        val tmpFile = Files.createTempFile("tdc", "");

        try (val ris = Objects.requireNonNull(this.getClass().getClassLoader().getResourceAsStream("config.txt"))) {
            Files.write(tmpFile, ris.readAllBytes());
        }
        configServer.stubFor(WireMock.get("/configs/test").willReturn(ok("Remote config")));
        val appId = "T001";
        val instanceId = UUID.randomUUID().toString();
        val instanceSpec = new ApplicationInstanceSpec(
                appId,
                "TEST_APP",
                instanceId,
                new DockerCoordinates(
                        APP_IMAGE_NAME,
                        Duration.seconds(100)),
                ImmutableList.of(new CPUAllocation(
                                         Collections.singletonMap(0,
                                                                  IntStream.range(0, numCpus)
                                                                          .boxed()
                                                                          .collect(Collectors.toUnmodifiableSet()))),
                                 new MemoryAllocation(Collections.singletonMap(0, 512L))),
                Collections.singletonList(new PortSpec("main", 3000, PortType.HTTP)),
                Collections.emptyList(),
                List.of(new InlineConfigSpec("/files/drove.txt",
                                             "Drove Test".getBytes(StandardCharsets.UTF_8)),
                        new ExecutorLocalFileConfigSpec("/files/drovelocal.txt",
                                                        tmpFile.toFile().getAbsolutePath()),
                        new ExecutorHttpFetchConfigSpec("/files/remote.txt",
                                                        HTTPCallSpec.builder()
                                                                .protocol(Protocol.HTTP)
                                                                .hostname("localhost")
                                                                .port(configServer.getPort())
                                                                .path("/configs/test")
                                                                .insecure(false)
                                                                .build())),
                null,
                null,
                LocalLoggingSpec.DEFAULT,
                Map.of("TEST_PREDEF_VAL", "PreDevValue",
                       "TEST_ENV_READ", "",
                       "TEST_ENV_READ_OVERRIDE", "OverriddenValue",
                       "TEST_ENV_UNDEFINED", ""
                      ),
                null,
                Collections.emptyList(),
                null,
                "TestToken");
        val executorId = CommonUtils.executorId(3000, "test-host");
        val ctx = new InstanceActionContext<>(executorId, instanceSpec, DOCKER_CLIENT, false);
        new ApplicationExecutableFetchAction(null).execute(ctx, StateData.create(InstanceState.PENDING, null));
        val resourceManager = mock(ResourceManager.class);
        when(resourceManager.currentState())
                .thenReturn(new ResourceInfo(null, null,
                                             new PhysicalLayout(Map.of(0, Set.of(0, 1, 2, 3)), Map.of(0, 1024L))));
        val newState
                = new ApplicationInstanceRunAction(
                resourceConfig, ExecutorOptions.DEFAULT, CommonTestUtils.httpCaller(), MAPPER,
                SharedMetricRegistries.getOrCreate("test"),
                resourceManager)
                .execute(ctx,
                         StateData.create(PROVISIONING,
                                          new ExecutorInstanceInfo(instanceSpec.getAppId(),
                                                                   instanceSpec.getAppName(),
                                                                   instanceSpec.getInstanceId(),
                                                                   executorId,
                                                                   new LocalInstanceInfo(CommonUtils.hostname(),
                                                                                         Collections.emptyMap()),
                                                                   instanceSpec.getResources(),
                                                                   Collections.emptyMap(),
                                                                   new Date(),
                                                                   new Date()),
                                          ""));
        assertEquals(InstanceState.UNREADY, newState.getState());

        //Test env injection
        assertEquals("PreDevValue", runCmd(ctx, "echo -n ${TEST_PREDEF_VAL}"));
        assertEquals("TestValue", runCmd(ctx, "echo -n ${TEST_ENV_READ}"));
        assertTrue(Strings.isNullOrEmpty(runCmd(ctx, "echo -n ${TEST_ENV_READ_INVALID}")));
        assertTrue(Strings.isNullOrEmpty(runCmd(ctx, "echo -n ${TEST_ENV_UNDEFINED}")));
        assertEquals("OverriddenValue", runCmd(ctx, "echo -n ${TEST_ENV_READ_OVERRIDE}"));
        //Test config injection
        assertEquals("Drove Test", runCmd(ctx, "cat /files/drove.txt"));
        assertEquals("Drove Local Config", runCmd(ctx, "cat /files/drovelocal.txt"));
        assertEquals("Remote config", runCmd(ctx, "cat /files/remote.txt"));
        val nprocOut = DockerUtils.runCommandInContainer(ctx.getDockerInstanceId(),
                                                         DOCKER_CLIENT,
                                                         "nproc|tr -d '\n'");
        log.info("nproc output: {}", nprocOut);
        val containerCpus = Integer.parseInt(nprocOut.getOutput());
        if(exactMatch) {
            assertEquals(numCpus, containerCpus);
        }
        else {
            val availableProcessors = Runtime.getRuntime().availableProcessors();
            if (numCpus != availableProcessors) {
                assertNotEquals(numCpus, containerCpus);
            }
        }
        DOCKER_CLIENT.stopContainerCmd(ctx.getDockerInstanceId()).exec();
        FileUtils.deleteQuietly(tmpFile.toFile());
    }

}