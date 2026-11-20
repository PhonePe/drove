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

import java.util.List;
import java.util.Set;

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
            ContainerSecurityProfile.DEFAULT_SECCOMP_PROFILE,
            ContainerSecurityProfile.DEFAULT_MAX_OPEN_FILES,
            ContainerSecurityProfile.DEFAULT_ALLOWED_EXECUTOR_ENV_VARS,
            ContainerSecurityProfile.DEFAULT_ALLOWED_CONFIG_HOST_DIRS
    );

    @Builder.Default
    Boolean enablePrivilegedMode = ContainerSecurityProfile.DEFAULT_ENABLE_PRIVILEGED_MODE;

    @Builder.Default
    Capabilities capabilities = Capabilities.DEFAULT;

    @Builder.Default
    String seccompProfile = ContainerSecurityProfile.DEFAULT_SECCOMP_PROFILE;

    @Builder.Default
    long maxOpenFiles = ContainerSecurityProfile.DEFAULT_MAX_OPEN_FILES;

    /**
     * Environment variable names an empty-valued env entry may resolve from
     * the executor process environment. Entries not on this list and without
     * a supplied value are ignored.
     */
    @Builder.Default
    Set<String> allowedExecutorEnvVars = ContainerSecurityProfile.DEFAULT_ALLOWED_EXECUTOR_ENV_VARS;

    /**
     * Host directories an EXECUTOR_LOCAL_FILE config may read from.
     * A path is allowed only when it resolves strictly below one of these
     * directories. Empty list means EXECUTOR_LOCAL_FILE configs are rejected.
     */
    @Builder.Default
    List<String> allowedConfigHostDirs = ContainerSecurityProfile.DEFAULT_ALLOWED_CONFIG_HOST_DIRS;

    public static DeployableSecuritySpec from(final ContainerSecurityProfile containerSecurityProfile) {
        return DeployableSecuritySpec.builder()
                .enablePrivilegedMode(containerSecurityProfile.getEnablePrivilegedMode())
                .capabilities(containerSecurityProfile.getCapabilities())
                .seccompProfile(containerSecurityProfile.getSeccompProfile())
                .maxOpenFiles(containerSecurityProfile.getMaxOpenFiles())
                .allowedExecutorEnvVars(containerSecurityProfile.getAllowedExecutorEnvVars())
                .allowedConfigHostDirs(containerSecurityProfile.getAllowedConfigHostDirs())
                .build();
    }
}
