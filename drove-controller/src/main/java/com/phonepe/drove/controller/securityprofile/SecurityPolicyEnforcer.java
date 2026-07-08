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

import java.util.List;
import java.util.Objects;

import javax.inject.Inject;

import com.google.common.annotations.VisibleForTesting;
import com.phonepe.drove.common.model.utils.Pair;
import com.phonepe.drove.controller.config.ControllerOptions;
import com.phonepe.drove.controller.securityprofile.ContainerSecurityPolicyConfig.ParsedPolicyRule;
import com.phonepe.drove.models.application.ApplicationSpec;
import com.phonepe.drove.models.interfaces.DeploymentSpec;
import com.phonepe.drove.models.interfaces.DeploymentSpecVisitor;
import com.phonepe.drove.models.localservice.LocalServiceSpec;
import com.phonepe.drove.models.policies.ContainerSecurityProfile;
import com.phonepe.drove.models.task.TaskSpec;

import lombok.NonNull;
import lombok.val;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class SecurityPolicyEnforcer {

    private final ContainerSecurityProfile defaultProfile;
    @NonNull
    private final List<Pair<ParsedPolicyRule, ContainerSecurityProfile>> policies;

    @Inject
    public SecurityPolicyEnforcer(@NonNull List<ContainerSecurityPolicyConfig> policies,
            @NonNull ControllerOptions controllerOptions) {
        this.defaultProfile = fromControllerOptions(controllerOptions);
        if (policies.isEmpty()) {
            this.policies = List.of(Pair.of(ParsedPolicyRule.from("*/*"), defaultProfile));
        }
        else {
            this.policies = policies.stream()
                    .flatMap(policy -> {
                        return policy.getMatch()
                                .stream()
                                .map(policyRule -> Pair.of(ParsedPolicyRule.from(policyRule), policy.getProfile()));
                    })
                    .toList();
        }
    }

    @VisibleForTesting
    static ContainerSecurityProfile fromControllerOptions(final ControllerOptions controllerOptions) {
        return ContainerSecurityProfile.builder()
                .policyName(ContainerSecurityProfile.DEFAULT_POLICY_NAME)
                .allowedMountDirs(Objects.requireNonNullElse(controllerOptions.getAllowedMountDirs(),
                                                             ContainerSecurityProfile.DEFAULT_ALLOWED_MOUNT_DIRS))
                .disableCmdlArgs(Objects.requireNonNullElse(controllerOptions.getDisableCmdlArgs(),
                                                            ContainerSecurityProfile.DEFAULT_DISABLE_CMDL_ARGS))
                .enableRawDeviceAccess(Objects.requireNonNullElse(controllerOptions.getEnableRawDeviceAccess(),
                                                                   ContainerSecurityProfile.DEFAULT_ENABLE_RAW_DEVICE_ACCESS))
                .capabilities(ContainerSecurityProfile.Capabilities.DEFAULT)
                .build();
    }
    
    public ContainerSecurityProfile resolve(final DeploymentSpec spec) {
        val matchable = matchableString(spec);        //Find all policies with this jobtype
        log.debug("Resolving security profile matchable: {}", matchable);
        return policies.stream()
            .filter(policy -> policy.getFirst().getApplicableTypes().contains(spec.getType()))
            .filter(policy -> policy.getFirst().getNamePattern().matcher(matchable).matches())
            .map(Pair::getSecond)
            .findFirst()
            .orElse(ContainerSecurityProfile.DEFAULT);
    }

    private static final String matchableString(final DeploymentSpec spec) {
        return spec.accept(new DeploymentSpecVisitor<String>() {

            @Override
            public String visit(ApplicationSpec applicationSpec) {
                return applicationSpec.getName();
            }

            @Override
            public String visit(TaskSpec taskSpec) {
                return taskSpec.getSourceAppName() + "/" + taskSpec.getTaskId();
            }

            @Override
            public String visit(LocalServiceSpec localServiceSpec) {
                return localServiceSpec.getName();
            }
        });
    }
}
