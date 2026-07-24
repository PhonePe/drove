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

package com.phonepe.drove.models.info;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nullable;

import com.phonepe.drove.models.application.JobType;
import com.phonepe.drove.models.application.MountedVolume;
import com.phonepe.drove.models.application.PortType;
import com.phonepe.drove.models.application.executable.ExecutableType;
import com.phonepe.drove.models.application.exposure.ExposureSpec;
import com.phonepe.drove.models.info.resources.allocation.ResourceAllocation;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.NonNull;
import lombok.Value;
import lombok.With;
import lombok.extern.jackson.Jacksonized;

@Value
@Builder
@With
@Jacksonized
@AllArgsConstructor
public class InjectedEnvironmentInfo {

    @Value
    @Builder
    @With
    @Jacksonized
    @AllArgsConstructor
    public static class Port {
        String name;
        int specifiedPort;
        int actualPort;
        PortType portType;
    }

    @Value
    @Builder
    @With
    @Jacksonized
    @AllArgsConstructor
    public static class Executor {
        @NonNull
        String executorId;
        
        @NonNull
        String hostname;

        @Builder.Default
        Set<String> tags = Set.of();

        @Builder.Default
        Map<String, String> metadata = Map.of();
    }

    @Value
    @Builder
    @With
    @Jacksonized
    @AllArgsConstructor
    public static class Parameters {
        ExecutableType type;

        String url;

        @Builder.Default
        List<String> args = List.of();

        @Builder.Default
        Map<String, String> tags = Map.of();
    }

    @Value
    @Builder
    @With
    @Jacksonized
    @AllArgsConstructor
    public static class Volume {
        @NonNull
        String path;
        @NonNull
        MountedVolume.MountMode mode;
    }


    @Value
    @Builder
    @With
    @Jacksonized
    @AllArgsConstructor
    public static class IO {

        @Builder.Default
        List<Port> ports = List.of();

        @Builder.Default
        List<ExposureSpec> vhosts = List.of();

        @Builder.Default
        List<Volume> volumes = List.of();
    }

    @Value
    @Builder
    @With
    @Jacksonized
    @AllArgsConstructor
    public static class Runtime {
        @Nullable
        String user;

        @Nullable
        String group;

        @Builder.Default
        List<ResourceAllocation> resources = List.of();

        @Builder.Default
        Map<String, String> env = Map.of();
    }

    @NonNull
    String instanceId;

    @NonNull
    JobType jobType;

    @NonNull
    Executor executor;

    @NonNull
    Parameters parameters;

    @NonNull
    IO io;

    @NonNull
    Runtime runtime;
   
    @Builder.Default
    Instant creationTime = Instant.now();
}
