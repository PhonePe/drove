/*
 * Copyright (c) 2026 Original Author(s), PhonePe India Pvt. Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.phonepe.drove.controller.securityprofile;

import com.phonepe.drove.models.policies.ContainerSecurityProfile;
import com.phonepe.drove.models.policies.ContainerSecurityProfile.Capabilities;
import com.phonepe.drove.models.policies.DeployableSecuritySpec;
import lombok.val;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class DeployableSecuritySpecTest {

    @Test
    void testDefaultSpecValues() {
        val def = DeployableSecuritySpec.DEFAULT;

        assertFalse(def.getEnablePrivilegedMode());
        assertEquals(Capabilities.DEFAULT, def.getCapabilities());
        assertEquals("", def.getSeccompProfile());
    }

    @Test
    void testFromContainerSecurityProfile() {
        val caps = Capabilities.builder()
                .addedCapabilities(Set.of("NET_ADMIN"))
                .droppedCapabilities(Set.of("MKNOD"))
                .build();

        val profile = ContainerSecurityProfile.builder()
                .policyName("test-policy")
                .enablePrivilegedMode(true)
                .capabilities(caps)
                .seccompProfile("/etc/seccomp/profile.json")
                // controller-side fields — must NOT appear on DeployableSecuritySpec
                .allowedMountDirs(List.of("/tmp"))
                .disableCmdlArgs(true)
                .enableRawDeviceAccess(true)
                .build();

        val spec = DeployableSecuritySpec.from(profile);

        assertTrue(spec.getEnablePrivilegedMode());
        assertEquals(caps, spec.getCapabilities());
        assertEquals("/etc/seccomp/profile.json", spec.getSeccompProfile());
    }

    @Test
    void testFromDefaultProfile() {
        val spec = DeployableSecuritySpec.from(ContainerSecurityProfile.DEFAULT);

        assertEquals(DeployableSecuritySpec.DEFAULT, spec);
        assertFalse(spec.getEnablePrivilegedMode());
        assertEquals(Capabilities.DEFAULT, spec.getCapabilities());
        assertEquals("", spec.getSeccompProfile());
    }

    @Test
    void testBuilderSetAllFields() {
        val caps = Capabilities.builder()
                .addedCapabilities(Set.of("SYS_TIME"))
                .droppedCapabilities(Set.of())
                .build();

        val spec = DeployableSecuritySpec.builder()
                .enablePrivilegedMode(true)
                .capabilities(caps)
                .seccompProfile("custom.json")
                .build();

        assertTrue(spec.getEnablePrivilegedMode());
        assertEquals(Set.of("SYS_TIME"), spec.getCapabilities().getAddedCapabilities());
        assertEquals("custom.json", spec.getSeccompProfile());
    }
}
