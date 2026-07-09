/*
 * Copyright 2026 authors
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

package com.phonepe.drove.models.policies;

import com.phonepe.drove.models.policies.ContainerSecurityProfile.Capabilities;

import lombok.Builder;
import lombok.Value;
import lombok.With;

@Value
@Builder
@With
public class DeployableSecuritySpec {

    public static final DeployableSecuritySpec DEFAULT = new DeployableSecuritySpec(
            ContainerSecurityProfile.DEFAULT_ENABLE_PRIVILEGED_MODE,
            Capabilities.DEFAULT,
            ContainerSecurityProfile.DEFAULT_SECCOMP_PROFILE
    );

    @Builder.Default
    Boolean enablePrivilegedMode = ContainerSecurityProfile.DEFAULT_ENABLE_PRIVILEGED_MODE;
    
    @Builder.Default
    Capabilities capabilities = Capabilities.DEFAULT;

    @Builder.Default
    String seccompProfile = ContainerSecurityProfile.DEFAULT_SECCOMP_PROFILE;

    public static DeployableSecuritySpec from(final ContainerSecurityProfile containerSecurityProfile) {
        return DeployableSecuritySpec.builder()
                .enablePrivilegedMode(containerSecurityProfile.getEnablePrivilegedMode())
                .capabilities(containerSecurityProfile.getCapabilities())
                .seccompProfile(containerSecurityProfile.getSeccompProfile())
                .build();
    }
}
