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

package com.phonepe.drove.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.phonepe.drove.common.model.ApplicationInstanceSpec;
import com.phonepe.drove.controller.securityprofile.ContainerSecurityPolicyConfig;
import com.phonepe.drove.models.policies.ContainerSecurityProfile;
import com.phonepe.drove.models.policies.DeployableSecuritySpec;
import lombok.SneakyThrows;
import lombok.val;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.phonepe.drove.common.CommonUtils.configureMapper;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for AppConfig securityPolicies-related field deserialization.
 * Uses JSON round-trips (avoids full Dropwizard config parsing complexity).
 */
class AppConfigTest {

    private static final ObjectMapper MAPPER;

    static {
        MAPPER = new ObjectMapper();
        configureMapper(MAPPER);
    }

    @Test
    @SneakyThrows
    void testSecurityPolicyConfigRoundTrip() {
        val original = ContainerSecurityPolicyConfig.builder()
                .match(List.of("SERVICE/MyApp.*"))
                .profile(ContainerSecurityProfile.builder()
                                 .policyName("privileged-policy")
                                 .enablePrivilegedMode(true)
                                 .build())
                .build();

        val json = MAPPER.writeValueAsString(original);
        val deserialized = MAPPER.readValue(json, ContainerSecurityPolicyConfig.class);

        assertEquals(List.of("SERVICE/MyApp.*"), deserialized.getMatch());
        assertNotNull(deserialized.getProfile());
        assertEquals("privileged-policy", deserialized.getProfile().getPolicyName());
        assertTrue(deserialized.getProfile().getEnablePrivilegedMode());
    }

    @Test
    @SneakyThrows
    void testSecurityPolicyConfigListRoundTrip() {
        val policies = List.of(
                ContainerSecurityPolicyConfig.builder()
                        .match(List.of("SERVICE/ProdApp.*"))
                        .profile(ContainerSecurityProfile.builder()
                                         .policyName("restricted")
                                         .enablePrivilegedMode(false)
                                         .build())
                        .build(),
                ContainerSecurityPolicyConfig.builder()
                        .match(List.of("COMPUTATION/BatchJob.*"))
                        .profile(ContainerSecurityProfile.builder()
                                         .policyName("privileged")
                                         .enablePrivilegedMode(true)
                                         .build())
                        .build()
        );

        val json = MAPPER.writeValueAsString(policies);
        val deserialized = MAPPER.readValue(json,
                                             new TypeReference<List<ContainerSecurityPolicyConfig>>() {});

        assertEquals(2, deserialized.size());
        assertEquals("restricted", deserialized.get(0).getProfile().getPolicyName());
        assertFalse(deserialized.get(0).getProfile().getEnablePrivilegedMode());
        assertEquals("privileged", deserialized.get(1).getProfile().getPolicyName());
        assertTrue(deserialized.get(1).getProfile().getEnablePrivilegedMode());
    }

    @Test
    @SneakyThrows
    void testContainerSecurityProfileRoundTrip() {
        val original = ContainerSecurityProfile.builder()
                .policyName("full-profile")
                .enablePrivilegedMode(true)
                .enableRawDeviceAccess(true)
                .disableCmdlArgs(true)
                .allowedMountDirs(List.of("/data", "/tmp"))
                .capabilities(ContainerSecurityProfile.Capabilities.builder()
                                      .addedCapabilities(Set.of("NET_ADMIN"))
                                      .droppedCapabilities(Set.of("SYS_TIME"))
                                      .build())
                .seccompProfile("runtime/default")
                .build();

        val json = MAPPER.writeValueAsString(original);
        val deserialized = MAPPER.readValue(json, ContainerSecurityProfile.class);

        assertEquals("full-profile", deserialized.getPolicyName());
        assertTrue(deserialized.getEnablePrivilegedMode());
        assertTrue(deserialized.getEnableRawDeviceAccess());
        assertTrue(deserialized.getDisableCmdlArgs());
        assertEquals(List.of("/data", "/tmp"), deserialized.getAllowedMountDirs());
        assertEquals("runtime/default", deserialized.getSeccompProfile());
        assertTrue(deserialized.getCapabilities().getAddedCapabilities().contains("NET_ADMIN"));
        assertTrue(deserialized.getCapabilities().getDroppedCapabilities().contains("SYS_TIME"));
    }

    @Test
    @SneakyThrows
    void testDeployableSecuritySpecRoundTrip() {
        val original = DeployableSecuritySpec.builder()
                .enablePrivilegedMode(true)
                .seccompProfile("runtime/default")
                .capabilities(ContainerSecurityProfile.Capabilities.builder()
                                      .addedCapabilities(Set.of("NET_ADMIN"))
                                      .droppedCapabilities(Set.of())
                                      .build())
                .build();

        val json = MAPPER.writeValueAsString(original);
        val deserialized = MAPPER.readValue(json, DeployableSecuritySpec.class);

        assertTrue(deserialized.getEnablePrivilegedMode());
        assertEquals("runtime/default", deserialized.getSeccompProfile());
        assertTrue(deserialized.getCapabilities().getAddedCapabilities().contains("NET_ADMIN"));
    }

    @Test
    @SneakyThrows
    void testDeployableSecuritySpecFromContainerSecurityProfile() {
        val profile = ContainerSecurityProfile.builder()
                .policyName("test-mapping")
                .enablePrivilegedMode(true)
                .enableRawDeviceAccess(true)
                .disableCmdlArgs(true)
                .allowedMountDirs(List.of("/restricted"))
                .seccompProfile("docker/default")
                .capabilities(ContainerSecurityProfile.Capabilities.builder()
                                      .addedCapabilities(Set.of("SYS_PTRACE"))
                                      .droppedCapabilities(Set.of())
                                      .build())
                .build();

        val spec = DeployableSecuritySpec.from(profile);

        assertTrue(spec.getEnablePrivilegedMode());
        assertEquals("docker/default", spec.getSeccompProfile());
        assertTrue(spec.getCapabilities().getAddedCapabilities().contains("SYS_PTRACE"));
    }

    @Test
    @SneakyThrows
    void testApplicationInstanceSpecWithSecuritySpecSerialization() {
        val securitySpec = DeployableSecuritySpec.builder()
                .enablePrivilegedMode(true)
                .seccompProfile("runtime/default")
                .capabilities(ContainerSecurityProfile.Capabilities.builder()
                                      .addedCapabilities(Set.of("NET_ADMIN"))
                                      .droppedCapabilities(Set.of())
                                      .build())
                .build();
        val appInstanceSpec = new ApplicationInstanceSpec(
                "app-01", "TestApp", "inst-01",
                null, List.of(), List.of(), List.of(), List.of(),
                null, null, null, Map.of(), null, List.of(), null, null,
                securitySpec, "token");

        val json = MAPPER.writeValueAsString(appInstanceSpec);
        val deserialized = MAPPER.readValue(json, ApplicationInstanceSpec.class);

        assertNotNull(deserialized.getSecuritySpec(),
                      "securitySpec must survive JSON round-trip");
        assertEquals(securitySpec.getEnablePrivilegedMode(),
                     deserialized.getSecuritySpec().getEnablePrivilegedMode());
        assertEquals(securitySpec.getSeccompProfile(),
                     deserialized.getSecuritySpec().getSeccompProfile());
        assertTrue(deserialized.getSecuritySpec().getCapabilities().getAddedCapabilities().contains("NET_ADMIN"));
    }
}
