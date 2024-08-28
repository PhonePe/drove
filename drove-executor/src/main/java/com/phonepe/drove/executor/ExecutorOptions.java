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

package com.phonepe.drove.executor;

import io.dropwizard.util.DataSize;
import io.dropwizard.util.DataSizeUnit;
import io.dropwizard.util.Duration;
import io.dropwizard.validation.DataSizeRange;
import io.dropwizard.validation.DurationRange;
import lombok.*;
import lombok.extern.jackson.Jacksonized;
import org.hibernate.validator.constraints.Length;
import org.hibernate.validator.constraints.Range;

import javax.validation.constraints.Min;
import java.util.concurrent.TimeUnit;

/**
 *
 */
@Value
@Jacksonized
@Builder
@AllArgsConstructor
@With
public class ExecutorOptions {
    public static final long DEFAULT_MAX_OPEN_FILES = 470_000;
    public static final DataSize DEFAULT_LOG_BUFFER_SIZE = DataSize.megabytes(10);
    public static final DataSize DEFAULT_LOG_CACHE_SIZE = DataSize.megabytes(20);
    public static final int DEFAULT_LOG_CACHE_COUNT = 3;
    public static final Duration DEFAULT_CONTAINER_COMMAND_TIMEOUT = Duration.seconds(30);
    @SuppressWarnings("java:S1075")
    public static final String DEFAULT_DOCKER_SOCKET_PATH = "/var/run/docker.sock";

    public static final ExecutorOptions DEFAULT = new ExecutorOptions(null,
                                                                      true,
                                                                      DEFAULT_MAX_OPEN_FILES,
                                                                      DEFAULT_LOG_BUFFER_SIZE,
                                                                      DEFAULT_LOG_CACHE_SIZE,
                                                                      DEFAULT_LOG_CACHE_COUNT,
                                                                      DEFAULT_CONTAINER_COMMAND_TIMEOUT,
                                                                      DEFAULT_DOCKER_SOCKET_PATH);

    @Length(max = 255)
    String hostname;

    boolean cacheImages;

    @Min(0)
    long maxOpenFiles;

    @DataSizeRange(min = 1, max = 128, unit = DataSizeUnit.MEGABYTES)
    DataSize logBufferSize;

    @DataSizeRange(min = 10, max = 100 * 1024, unit = DataSizeUnit.MEGABYTES)
    DataSize cacheFileSize;

    @Range(max = 1024)
    int cacheFileCount;

    @DurationRange(min = 5, max = 300, unit = TimeUnit.SECONDS)
    Duration containerCommandTimeout;

    String dockerSocketPath;
}
