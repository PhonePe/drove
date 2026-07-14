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

import static com.phonepe.drove.controller.ControllerTestUtils.appSpec;
import static com.phonepe.drove.controller.ControllerTestUtils.localServiceSpec;
import static com.phonepe.drove.controller.ControllerTestUtils.taskSpec;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;

import com.phonepe.drove.controller.config.ControllerOptions;
import com.phonepe.drove.models.policies.ContainerSecurityProfile;

import org.junit.jupiter.api.Test;

import lombok.val;

class SecurityPolicyEnforcerTest {

    @Test
    void testDefaultProfile() {
        val enforcer = new SecurityPolicyEnforcer(List.of(), ControllerOptions.DEFAULT);
        val profile = enforcer.resolve(appSpec());

        assertEquals(ContainerSecurityProfile.DEFAULT_POLICY_NAME, profile.getPolicyName());
        assertEquals(ControllerOptions.DEFAULT_ALLOWED_MOUNT_DIRS, profile.getAllowedMountDirs());
        assertFalse(profile.getDisableCmdlArgs());
        assertFalse(profile.getEnableRawDeviceAccess());
    }

    @Test
    void testDefaultProfileCustomOptions() {
        val opts = ControllerOptions.DEFAULT
                .withAllowedMountDirs(List.of("/tmp", "/home"))
                .withDisableCmdlArgs(true)
                .withEnableRawDeviceAccess(true);
        val enforcer = new SecurityPolicyEnforcer(List.of(), opts);
        val profile = enforcer.resolve(appSpec());

        assertEquals(List.of("/tmp", "/home"), profile.getAllowedMountDirs());
        assertTrue(profile.getDisableCmdlArgs());
        assertTrue(profile.getEnableRawDeviceAccess());
        assertEquals(ContainerSecurityProfile.DEFAULT_POLICY_NAME, profile.getPolicyName());
    }

    @Test
    void testResolveAppByName() {
        val customProfile = buildProfile("custom-app-policy");
        val enforcer = createEnforcerWithPolicy("SERVICE/MyApp.*", customProfile);

        val matched = enforcer.resolve(appSpec("MyApp123", 1));
        assertEquals("custom-app-policy", matched.getPolicyName());

        val notMatched = enforcer.resolve(appSpec("OtherApp", 1));
        assertEquals(ContainerSecurityProfile.DEFAULT_POLICY_NAME, notMatched.getPolicyName());
    }

    // The matchable string for TaskSpec is: sourceAppName + "/" + taskId
    // Rule format is (jobtype)/(name-regex) — at most 2 slash parts.
    // Use "COMPUTATION/MyApp.*" so the regex "MyApp.*" matches "MyApp/Task123" (.*  spans the "/").
    @Test
    void testResolveTaskByName() {
        val customProfile = buildProfile("task-policy");
        val enforcer = createEnforcerWithPolicy("COMPUTATION/MyApp.*", customProfile);

        val matchedTask = taskSpec("MyApp", "Task123");
        assertEquals("task-policy", enforcer.resolve(matchedTask).getPolicyName());

        val nonMatchedTask = taskSpec("OtherApp", "Task123");
        assertEquals(ContainerSecurityProfile.DEFAULT_POLICY_NAME,
                     enforcer.resolve(nonMatchedTask).getPolicyName());
    }

    @Test
    void testResolveLocalServiceByName() {
        val customProfile = buildProfile("ls-policy");
        val enforcer = createEnforcerWithPolicy("LOCAL_SERVICE/MyService.*", customProfile);

        val matched = localServiceSpec("MyServiceXYZ", 1);
        assertEquals("ls-policy", enforcer.resolve(matched).getPolicyName());
    }

    @Test
    void testResolveWildcardJobType() {
        val customProfile = buildProfile("wildcard-jobtype-policy");
        val enforcer = createEnforcerWithPolicy("*/MyApp.*", customProfile);

        assertEquals("wildcard-jobtype-policy", enforcer.resolve(appSpec("MyApp123", 1)).getPolicyName());
        assertEquals("wildcard-jobtype-policy", enforcer.resolve(taskSpec("MyApp", "MyAppTask")).getPolicyName());
        assertEquals("wildcard-jobtype-policy",
                     enforcer.resolve(localServiceSpec("MyAppLS", 1)).getPolicyName());
    }

    @Test
    void testResolveWildcardName() {
        val customProfile = buildProfile("service-wildcard-policy");
        val enforcer = createEnforcerWithPolicy("SERVICE/*", customProfile);

        assertEquals("service-wildcard-policy", enforcer.resolve(appSpec("AnyAppName", 1)).getPolicyName());

        val taskNoMatch = enforcer.resolve(taskSpec("AnyTask", "T1"));
        assertEquals(ContainerSecurityProfile.DEFAULT_POLICY_NAME, taskNoMatch.getPolicyName());
    }

    @Test
    void testResolveFullWildcard() {
        val customProfile = buildProfile("full-wildcard-policy");
        val enforcer = createEnforcerWithPolicy("*/*", customProfile);

        assertEquals("full-wildcard-policy", enforcer.resolve(appSpec()).getPolicyName());
        assertEquals("full-wildcard-policy", enforcer.resolve(taskSpec()).getPolicyName());
        assertEquals("full-wildcard-policy", enforcer.resolve(localServiceSpec()).getPolicyName());
    }

    @Test
    void testFirstMatchWins() {
        val profileA = buildProfile("profile-a");
        val profileB = buildProfile("profile-b");

        val configA = ContainerSecurityPolicyConfig.builder()
                .match(List.of("SERVICE/My.*"))
                .profile(profileA)
                .build();
        val configB = ContainerSecurityPolicyConfig.builder()
                .match(List.of("SERVICE/MyApp.*"))
                .profile(profileB)
                .build();
        val enforcer = new SecurityPolicyEnforcer(List.of(configA, configB), ControllerOptions.DEFAULT);

        val resolved = enforcer.resolve(appSpec("MyApp123", 1));
        assertEquals("profile-a", resolved.getPolicyName());
    }

    @Test
    void testNoMatchDefaultFallback() {
        val customProfile = buildProfile("specific-policy");
        val enforcer = createEnforcerWithPolicy("SERVICE/NonMatching.*", customProfile);

        val resolved = enforcer.resolve(appSpec("MyApp", 1));
        assertEquals(ContainerSecurityProfile.DEFAULT_POLICY_NAME, resolved.getPolicyName());
        assertEquals(ContainerSecurityProfile.DEFAULT, resolved);
    }

    @Test
    void testMultipleMatchRules() {
        val customProfile = buildProfile("multi-rule-policy");
        val config = ContainerSecurityPolicyConfig.builder()
                .match(List.of("SERVICE/App1", "SERVICE/App2"))
                .profile(customProfile)
                .build();
        val enforcer = new SecurityPolicyEnforcer(List.of(config), ControllerOptions.DEFAULT);

        assertEquals("multi-rule-policy", enforcer.resolve(appSpec("App1", 1)).getPolicyName());
        assertEquals("multi-rule-policy", enforcer.resolve(appSpec("App2", 1)).getPolicyName());
        assertEquals(ContainerSecurityProfile.DEFAULT_POLICY_NAME,
                     enforcer.resolve(appSpec("App3", 1)).getPolicyName());
    }

    @Test
    void testFromControllerOptions() {
        val opts = ControllerOptions.DEFAULT
                .withAllowedMountDirs(List.of("/data"))
                .withDisableCmdlArgs(true)
                .withEnableRawDeviceAccess(true);

        val profile = SecurityPolicyEnforcer.fromControllerOptions(opts);

        assertEquals(ContainerSecurityProfile.DEFAULT_POLICY_NAME, profile.getPolicyName());
        assertEquals(List.of("/data"), profile.getAllowedMountDirs());
        assertTrue(profile.getDisableCmdlArgs());
        assertTrue(profile.getEnableRawDeviceAccess());
        assertEquals(ContainerSecurityProfile.Capabilities.DEFAULT, profile.getCapabilities());
        assertFalse(profile.getEnablePrivilegedMode());
        assertEquals("", profile.getSeccompProfile());
    }


    private SecurityPolicyEnforcer createEnforcerWithPolicy(String matchRule,
                                                             ContainerSecurityProfile profile) {
        val config = ContainerSecurityPolicyConfig.builder()
                .match(List.of(matchRule))
                .profile(profile)
                .build();
        return new SecurityPolicyEnforcer(List.of(config), ControllerOptions.DEFAULT);
    }

    private ContainerSecurityProfile buildProfile(String name) {
        return ContainerSecurityProfile.builder()
                .policyName(name)
                .allowedMountDirs(List.of("/tmp"))
                .disableCmdlArgs(false)
                .enablePrivilegedMode(true)
                .enableRawDeviceAccess(true)
                .capabilities(ContainerSecurityProfile.Capabilities.builder()
                                       .addedCapabilities(Set.of("NET_ADMIN"))
                                       .droppedCapabilities(Set.of())
                                       .build())
                .seccompProfile("")
                .build();
    }
}
