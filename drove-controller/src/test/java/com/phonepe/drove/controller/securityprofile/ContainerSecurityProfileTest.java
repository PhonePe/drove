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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import com.phonepe.drove.models.policies.ContainerSecurityProfile;
import com.phonepe.drove.models.policies.ContainerSecurityProfile.Capabilities;

import org.junit.jupiter.api.Test;

import lombok.val;

class ContainerSecurityProfileTest {

    @Test
    void testDefaultProfileValues() {
        val def = ContainerSecurityProfile.DEFAULT;

        assertEquals(ContainerSecurityProfile.DEFAULT_POLICY_NAME, def.getPolicyName());
        assertEquals(List.of(), def.getAllowedMountDirs());
        assertFalse(def.getDisableCmdlArgs());
        assertFalse(def.getEnablePrivilegedMode());
        assertFalse(def.getEnableRawDeviceAccess());
        assertEquals(Capabilities.DEFAULT, def.getCapabilities());
        assertEquals("", def.getSeccompProfile());
    }

    @Test
    void testCapabilitiesDefault() {
        val caps = Capabilities.DEFAULT;

        assertNotNull(caps.getAddedCapabilities());
        assertNotNull(caps.getDroppedCapabilities());
        assertTrue(caps.getAddedCapabilities().isEmpty());
        assertTrue(caps.getDroppedCapabilities().isEmpty());
    }
}
