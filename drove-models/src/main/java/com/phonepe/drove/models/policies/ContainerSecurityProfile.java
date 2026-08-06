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

package com.phonepe.drove.models.policies;

import java.util.List;
import java.util.Set;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;

import lombok.Builder;
import lombok.NonNull;
import lombok.Value;
import lombok.With;
import lombok.extern.jackson.Jacksonized;

/**
 * ContainerSecurityPolicy defines the security policies for a container.
 */
@Value
@Builder
@With
@Jacksonized
public class ContainerSecurityProfile {

    @Value
    @Builder
    @With
    public static class Capabilities {
        public static final Capabilities DEFAULT = Capabilities.builder()
                .addedCapabilities(Set.of())
                .droppedCapabilities(Set.of())
                .build();
        /**
         * Capabilities that are added to the container in addition to the default set of capabilities.
         */
        Set<String> addedCapabilities;

        /**
         * Capabilities that are removed from the container in addition to the default set of capabilities.
         */
        Set<String> droppedCapabilities;

    }

    public static final String DEFAULT_POLICY_NAME = "__default__";
    public static final List<String> DEFAULT_ALLOWED_MOUNT_DIRS = List.of();
    public static final Boolean DEFAULT_DISABLE_CMDL_ARGS = false;
    public static final Boolean DEFAULT_ENABLE_PRIVILEGED_MODE = false;
    public static final Boolean DEFAULT_ENABLE_RAW_DEVICE_ACCESS = false;
    public static final Capabilities DEFAULT_CAPABILITIES = Capabilities.DEFAULT;
    public static final long DEFAULT_MAX_OPEN_FILES = 470_000;
    public static final String DEFAULT_SECCOMP_PROFILE = "";

    public static final ContainerSecurityProfile DEFAULT = ContainerSecurityProfile.builder()
            .policyName(DEFAULT_POLICY_NAME)
            .allowedMountDirs(DEFAULT_ALLOWED_MOUNT_DIRS)
            .disableCmdlArgs(DEFAULT_DISABLE_CMDL_ARGS)
            .enablePrivilegedMode(DEFAULT_ENABLE_PRIVILEGED_MODE)
            .enableRawDeviceAccess(DEFAULT_ENABLE_RAW_DEVICE_ACCESS)
            .capabilities(Capabilities.DEFAULT)
            .maxOpenFiles(DEFAULT_MAX_OPEN_FILES)
            .seccompProfile(DEFAULT_SECCOMP_PROFILE)
            .build();

    @NonNull
    @NotNull
    String policyName;

    /**
     * Allowed mount directories for the container.
     * If not specified, no restrictions are applied.
     * This is a list of directory paths.
     */
    @Builder.Default
    List<String> allowedMountDirs = DEFAULT_ALLOWED_MOUNT_DIRS;

    /**
     * Whether to disable command line arguments for the container.
     * If true, the container will not be able to use command line arguments.
     * Default is false (command line arguments are allowed).
     */
    @Builder.Default
    Boolean disableCmdlArgs = DEFAULT_DISABLE_CMDL_ARGS;

    /**
     * Run container in privileged mode, which gives it elevated permissions on the host.
     */
    @Builder.Default
    Boolean enablePrivilegedMode = DEFAULT_ENABLE_PRIVILEGED_MODE;

    /**
     * Whether to allow access to raw devices from the container.
     * If true, the container will be able to access raw devices on the host.
     * Default is false (raw device access is not allowed).
     */
    @Builder.Default
    Boolean enableRawDeviceAccess = DEFAULT_ENABLE_RAW_DEVICE_ACCESS;

    /**
     * List of allowed capabilities for the container.
     * If not specified, no capabilities are allowed.
     * This is a list of capability names (e.g., "NET_ADMIN", "SYS_TIME").
     */
    @Builder.Default
    Capabilities capabilities = Capabilities.DEFAULT;

    /**
     * Maximum number of open files allowed for the container.
     * If not specified, the default limit will be applied.
     */
    @Builder.Default
    @Min(0)
    long maxOpenFiles = DEFAULT_MAX_OPEN_FILES;

    /**
     * List of allowed security contexts for the container.
     * If not specified, the default policy will be applied.
     */
    @Builder.Default
    String seccompProfile = DEFAULT_SECCOMP_PROFILE;
}
