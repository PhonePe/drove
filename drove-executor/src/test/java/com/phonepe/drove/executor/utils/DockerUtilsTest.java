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

package com.phonepe.drove.executor.utils;

import static com.phonepe.drove.executor.ExecutorTestingUtils.DOCKER_CLIENT;
import static com.phonepe.drove.executor.ExecutorTestingUtils.testTaskInstanceSpec;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Stream;

import com.github.dockerjava.api.model.Capability;
import com.phonepe.drove.common.CommonUtils;
import com.phonepe.drove.common.model.TaskInstanceSpec;
import com.phonepe.drove.common.net.HttpCaller;
import com.phonepe.drove.executor.ExecutorOptions;
import com.phonepe.drove.executor.ExecutorTestingUtils;
import com.phonepe.drove.executor.resourcemgmt.OverProvisioning;
import com.phonepe.drove.executor.resourcemgmt.ResourceConfig;
import com.phonepe.drove.executor.resourcemgmt.ResourceInfo;
import com.phonepe.drove.executor.resourcemgmt.ResourceManager;
import com.phonepe.drove.models.application.devices.DirectDeviceSpec;
import com.phonepe.drove.models.info.resources.PhysicalLayout;
import com.phonepe.drove.models.policies.ContainerSecurityProfile;
import com.phonepe.drove.models.policies.DeployableSecuritySpec;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import lombok.val;

/**
 *
 */
class DockerUtilsTest {

    @Test
    void testCreateContainerNullSecuritySpec() {
        val resourceManager = mockResourceManager();
        val spec = testTaskInstanceSpec().withSecuritySpec(null);
        pullImage(spec);
        val containerId = DockerUtils.createContainer(
                ResourceConfig.DEFAULT, DOCKER_CLIENT, CommonUtils.instanceId(spec),
                spec, dockerRunParams -> {}, ExecutorOptions.DEFAULT, resourceManager);
        try {
            val inspected = DOCKER_CLIENT.inspectContainerCmd(containerId).exec();
            assertNotNull(inspected);
            val hostConfig = inspected.getHostConfig();
            assertNotNull(hostConfig);
            assertNotNull(hostConfig);
            assertNotEquals(Boolean.TRUE, hostConfig.getPrivileged());
        }
        finally {
            DOCKER_CLIENT.removeContainerCmd(containerId).withForce(true).exec();
        }
    }


    @Test
    void testCreateContainerPrivileged() {
        val resourceManager = mockResourceManager();
        val spec = testTaskInstanceSpec().withSecuritySpec(
                DeployableSecuritySpec.builder()
                        .enablePrivilegedMode(true)
                        .capabilities(ContainerSecurityProfile.Capabilities.DEFAULT)
                        .seccompProfile("")
                        .build());
        pullImage(spec);
        val containerId = DockerUtils.createContainer(
                ResourceConfig.DEFAULT, DOCKER_CLIENT, CommonUtils.instanceId(spec),
                spec, dockerRunParams -> {}, ExecutorOptions.DEFAULT, resourceManager);
        try {
            val hostConfig = DOCKER_CLIENT.inspectContainerCmd(containerId).exec().getHostConfig();
            assertNotNull(hostConfig);
            assertEquals(Boolean.TRUE, hostConfig.getPrivileged());
        }
        finally {
            DOCKER_CLIENT.removeContainerCmd(containerId).withForce(true).exec();
        }
    }


    @Test
    void testCreateContainerAddedCaps() {
        val resourceManager = mockResourceManager();
        val spec = testTaskInstanceSpec().withSecuritySpec(
                DeployableSecuritySpec.builder()
                        .enablePrivilegedMode(false)
                        .capabilities(ContainerSecurityProfile.Capabilities.builder()
                                              .addedCapabilities(Set.of("NET_ADMIN"))
                                              .droppedCapabilities(Set.of())
                                              .build())
                        .seccompProfile("")
                        .build());
        pullImage(spec);
        val containerId = DockerUtils.createContainer(
                ResourceConfig.DEFAULT, DOCKER_CLIENT, CommonUtils.instanceId(spec),
                spec, dockerRunParams -> {}, ExecutorOptions.DEFAULT, resourceManager);
        try {
            val hostConfig = DOCKER_CLIENT.inspectContainerCmd(containerId).exec().getHostConfig();
            assertNotNull(hostConfig);
            assertNotNull(hostConfig.getCapAdd());
            assertTrue(List.of(hostConfig.getCapAdd()).contains(Capability.NET_ADMIN),
                       "CapAdd should contain NET_ADMIN");
        }
        finally {
            DOCKER_CLIENT.removeContainerCmd(containerId).withForce(true).exec();
        }
    }


    @Test
    void testCreateContainerDroppedCaps() {
        val resourceManager = mockResourceManager();
        val spec = testTaskInstanceSpec().withSecuritySpec(
                DeployableSecuritySpec.builder()
                        .enablePrivilegedMode(false)
                        .capabilities(ContainerSecurityProfile.Capabilities.builder()
                                              .addedCapabilities(Set.of())
                                              .droppedCapabilities(Set.of("MKNOD"))
                                              .build())
                        .seccompProfile("")
                        .build());
        pullImage(spec);
        val containerId = DockerUtils.createContainer(
                ResourceConfig.DEFAULT, DOCKER_CLIENT, CommonUtils.instanceId(spec),
                spec, dockerRunParams -> {}, ExecutorOptions.DEFAULT, resourceManager);
        try {
            val hostConfig = DOCKER_CLIENT.inspectContainerCmd(containerId).exec().getHostConfig();
            assertNotNull(hostConfig);
            assertNotNull(hostConfig.getCapDrop());
            assertTrue(List.of(hostConfig.getCapDrop()).contains(Capability.MKNOD),
                       "CapDrop should contain MKNOD");
        }
        finally {
            DOCKER_CLIENT.removeContainerCmd(containerId).withForce(true).exec();
        }
    }


    @Test
    void testCreateContainerSeccompProfile() {
        val resourceManager = mockResourceManager();
        val seccompPath = "/dev/null"; // a file that exists on any Linux host
        val spec = testTaskInstanceSpec().withSecuritySpec(
                DeployableSecuritySpec.builder()
                        .enablePrivilegedMode(false)
                        .capabilities(ContainerSecurityProfile.Capabilities.DEFAULT)
                        .seccompProfile(seccompPath)
                        .build());
        pullImage(spec);
        val containerId = DockerUtils.createContainer(
                ResourceConfig.DEFAULT, DOCKER_CLIENT, CommonUtils.instanceId(spec),
                spec, dockerRunParams -> {}, ExecutorOptions.DEFAULT, resourceManager);
        try {
            val hostConfig = DOCKER_CLIENT.inspectContainerCmd(containerId).exec().getHostConfig();
            assertNotNull(hostConfig);
            assertNotNull(hostConfig.getSecurityOpts());
            assertTrue(hostConfig.getSecurityOpts().stream()
                               .anyMatch(o -> o.startsWith("seccomp=")),
                       "SecurityOpts should contain a seccomp= entry");
        }
        finally {
            DOCKER_CLIENT.removeContainerCmd(containerId).withForce(true).exec();
        }
    }


    @Test
    void testCreateContainerDefaultSecuritySpec() {
        val resourceManager = mockResourceManager();
        val spec = testTaskInstanceSpec().withSecuritySpec(DeployableSecuritySpec.DEFAULT);
        pullImage(spec);
        val containerId = DockerUtils.createContainer(
                ResourceConfig.DEFAULT, DOCKER_CLIENT, CommonUtils.instanceId(spec),
                spec, dockerRunParams -> {}, ExecutorOptions.DEFAULT, resourceManager);
        try {
            val hostConfig = DOCKER_CLIENT.inspectContainerCmd(containerId).exec().getHostConfig();
            assertNotNull(hostConfig);
            assertNotEquals(Boolean.TRUE, hostConfig.getPrivileged());
            val capAdd = hostConfig.getCapAdd();
            assertTrue(capAdd == null || capAdd.length == 0,
                       "No capabilities should be added with DEFAULT securitySpec");
            val securityOpts = hostConfig.getSecurityOpts();
            assertTrue(securityOpts == null || securityOpts.stream().noneMatch(o -> o.startsWith("seccomp=")),
                       "No seccomp opt should be set with DEFAULT securitySpec");
        }
        finally {
            DOCKER_CLIENT.removeContainerCmd(containerId).withForce(true).exec();
        }
    }


    @Test
    void testCreateContainerEmptyCaps() {
        val resourceManager = mockResourceManager();
        val spec = testTaskInstanceSpec().withSecuritySpec(
                DeployableSecuritySpec.builder()
                        .enablePrivilegedMode(false)
                        .capabilities(ContainerSecurityProfile.Capabilities.builder()
                                              .addedCapabilities(Set.of())
                                              .droppedCapabilities(Set.of())
                                              .build())
                        .seccompProfile("")
                        .build());
        pullImage(spec);
        val containerId = DockerUtils.createContainer(
                ResourceConfig.DEFAULT, DOCKER_CLIENT, CommonUtils.instanceId(spec),
                spec, dockerRunParams -> {}, ExecutorOptions.DEFAULT, resourceManager);
        try {
            val hostConfig = DOCKER_CLIENT.inspectContainerCmd(containerId).exec().getHostConfig();
            assertNotNull(hostConfig);
            val capAdd = hostConfig.getCapAdd();
            assertTrue(capAdd == null || capAdd.length == 0, "No caps should be added");
            val capDrop = hostConfig.getCapDrop();
            assertTrue(capDrop == null || capDrop.length == 0, "No caps should be dropped");
        }
        finally {
            DOCKER_CLIENT.removeContainerCmd(containerId).withForce(true).exec();
        }
    }


    @Test
    void testCreateContainerInvalidCapThrows() {
        val resourceManager = mockResourceManager();
        val spec = testTaskInstanceSpec().withSecuritySpec(
                DeployableSecuritySpec.builder()
                        .enablePrivilegedMode(false)
                        .capabilities(ContainerSecurityProfile.Capabilities.builder()
                                              .addedCapabilities(Set.of("INVALID_CAP_XYZ"))
                                              .droppedCapabilities(Set.of())
                                              .build())
                        .seccompProfile("")
                        .build());
        pullImage(spec);
        assertThrows(IllegalArgumentException.class, () -> DockerUtils.createContainer(
                ResourceConfig.DEFAULT, DOCKER_CLIENT, CommonUtils.instanceId(spec),
                spec, dockerRunParams -> {}, ExecutorOptions.DEFAULT, resourceManager));
    }


    private static ResourceManager mockResourceManager() {
        val resourceManager = mock(ResourceManager.class);
        when(resourceManager.currentState())
                .thenReturn(new ResourceInfo(null, null,
                                             new PhysicalLayout(Map.of(0, Set.of(0, 1, 2, 3)), Map.of(0, 1024L))));
        return resourceManager;
    }

    @ParameterizedTest
    @MethodSource("generateResourceConfigs")
    void testContainerCreate(
            final ResourceConfig resourceConfig,
            final TaskInstanceSpec actualSpec,
            final ExecutorOptions executorOptions) {
        val env = new HashMap<>(actualSpec.getEnv());
        env.putAll(Map.of("ITERATIONS", "10000", "LOOP_SLEEP", "5"));
        val spec = actualSpec.withEnv(env);
        val resourceManager = mock(ResourceManager.class);
        when(resourceManager.currentState())
                .thenReturn(new ResourceInfo(null, null, null,
                                             new PhysicalLayout(Map.of(0, Set.of(0, 1, 2, 3)), Map.of(0, 1024L), Map.of())));
        pullImage(spec);
        val containerId = DockerUtils.createContainer(
                resourceConfig,
                DOCKER_CLIENT,
                CommonUtils.instanceId(spec),
                spec,
                dockerRunParams -> {},
                executorOptions,
                resourceManager);
        try {
            assertNotNull(DOCKER_CLIENT.inspectContainerCmd(containerId).exec());
        }
        finally {
            DOCKER_CLIENT.removeContainerCmd(containerId).withForce(true).exec();
        }
    }

    @Test
    void testContainerRun() {
        val resourceManager = mock(ResourceManager.class);
        when(resourceManager.currentState())
                .thenReturn(new ResourceInfo(null, null, null,
                                             new PhysicalLayout(Map.of(0, Set.of(0, 1, 2, 3)), Map.of(0, 1024L), Map.of())));
        val httpCaller = mock(HttpCaller.class);
        val spec = ExecutorTestingUtils.testTaskInstanceSpec()
                .withEnv(Map.of("ITERATIONS", "10000", "LOOP_SLEEP", "5"));
        pullImage(spec);
        val containerId = DockerUtils.createContainer(
                ResourceConfig.DEFAULT,
                DOCKER_CLIENT,
                CommonUtils.instanceId(spec),
                spec,
                dockerRunParams -> {},
                ExecutorOptions.DEFAULT,
                resourceManager);
        try {
            DockerUtils.injectConfigs(containerId,
                                      DOCKER_CLIENT,
                                      spec,
                                      httpCaller);
            assertNotNull(DOCKER_CLIENT.inspectContainerCmd(containerId).exec());
            DOCKER_CLIENT.startContainerCmd(containerId).exec();
            val res = DockerUtils.runCommandInContainer(containerId, DOCKER_CLIENT, "echo hello");
            assertEquals(0, res.getStatus());
        }
        finally {
            DOCKER_CLIENT.removeContainerCmd(containerId).withForce(true).exec();
        }
    }

    private static void pullImage(TaskInstanceSpec spec) {
        try {
            val imageId = DockerUtils.pullImage(DOCKER_CLIENT, null, spec, Function.identity());
            assertNotNull(DOCKER_CLIENT.inspectImageCmd(imageId).exec());
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            Assertions.fail("Pull Interrupted", e);
        }
    }

    private static Stream<Arguments> generateResourceConfigs() {
        val spec = testTaskInstanceSpec();
        val defaultConfig = ResourceConfig.DEFAULT;
        val executorOptions = ExecutorOptions.DEFAULT;
        return Stream.of(
                Arguments.of(defaultConfig, spec, executorOptions),
                Arguments.of(
                        defaultConfig
                                .setOverProvisioning(new OverProvisioning()
                                                              .setEnabled(true)
                                                              .setCpuMultiplier(1)
                                                              .setMemoryMultiplier(1)),
                        spec.withConfigs(null),
                        executorOptions),
                Arguments.of(defaultConfig, spec, executorOptions
                        .withMaxOpenFiles(-1)
                        .withCacheFileCount(-1)),
                Arguments.of(defaultConfig,
                             spec
                                     .withEnv(Map.of("TEST_PREDEF_VAL", "PreDevValue",
                                                     "TEST_ENV_READ", "",
                                                     "TEST_ENV_READ_OVERRIDE", "OverriddenValue",
                                                     "TEST_ENV_UNDEFINED", ""
                                                    ))
                                     .withArgs(List.of("./script.sh"))
                                     .withConfigs(List.of()),
                             executorOptions),
                Arguments.of(defaultConfig,
                             spec.withDevices(List.of(DirectDeviceSpec.builder()
                                                              .pathOnHost("/dev/random")
                                                              .pathInContainer("dev/random")
                                                              .permissions(DirectDeviceSpec.DirectDevicePermissions.READ_ONLY)
                                                              .build())),
                             executorOptions),
                Arguments.of(defaultConfig,
                             spec.withDevices(List.of(DirectDeviceSpec.builder()
                                                              .pathOnHost("/dev/random")
                                                              .pathInContainer("dev/random")
                                                              .permissions(DirectDeviceSpec.DirectDevicePermissions.WRITE_ONLY)
                                                              .build())),
                             executorOptions),
                Arguments.of(defaultConfig,
                             spec.withDevices(List.of(DirectDeviceSpec.builder()
                                                              .pathOnHost("/dev/random")
                                                              .pathInContainer("dev/random")
                                                              .permissions(DirectDeviceSpec.DirectDevicePermissions.MKNOD_ONLY)
                                                              .build())),
                             executorOptions),
                Arguments.of(defaultConfig,
                             spec.withDevices(List.of(DirectDeviceSpec.builder()
                                                              .pathOnHost("/dev/random")
                                                              .pathInContainer("dev/random")
                                                              .permissions(DirectDeviceSpec.DirectDevicePermissions.READ_WRITE)
                                                              .build())),
                             executorOptions));
    }

}
