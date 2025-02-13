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

package com.phonepe.drove.controller.config;

import com.phonepe.drove.models.operation.ClusterOpSpec;
import io.dropwizard.util.Duration;
import io.dropwizard.validation.DurationRange;
import io.dropwizard.validation.MinDuration;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Value;
import lombok.With;
import lombok.extern.jackson.Jacksonized;
import org.hibernate.validator.constraints.Range;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 *
 */
@Value
@Jacksonized
@Builder
@AllArgsConstructor
@With
public class ControllerOptions {
    public static final Duration DEFAULT_STALE_CHECK_INTERVAL = Duration.hours(1);
    public static final Duration DEFAULT_STALE_APP_AGE = Duration.days(7);
    public static final int DEFAULT_MAX_STALE_INSTANCES_COUNT = 100;
    public static final Duration DEFAULT_STALE_INSTANCE_AGE = Duration.days(7);
    public static final Duration DEFAULT_STALE_TASK_AGE = Duration.days(2);
    public static final Duration DEFAULT_STALE_SERVICE_AGE = Duration.days(7);
    public static final Duration DEFAULT_MAX_EVENT_STORAGE_DURATION = Duration.minutes(60);
    public static final int DEFAULT_JOB_RETRY_COUNT = 2;
    public static final Duration DEFAULT_JOB_RETRY_INTERVAL = Duration.seconds(1);
    public static final Duration DEFAULT_INSTANCE_STATE_CHECK_RETRY_INTERVAL = Duration.seconds(3);
    public static final Set<String> DEFAULT_AUDITED_METHODS = Set.of("POST", "PUT");
    public static final List<String> DEFAULT_ALLOWED_MOUNT_DIRS = List.of();

    public static final ControllerOptions DEFAULT = new ControllerOptions(DEFAULT_STALE_CHECK_INTERVAL,
                                                                          DEFAULT_STALE_APP_AGE,
                                                                          DEFAULT_MAX_STALE_INSTANCES_COUNT,
                                                                          DEFAULT_STALE_INSTANCE_AGE,
                                                                          DEFAULT_STALE_TASK_AGE,
                                                                          DEFAULT_STALE_SERVICE_AGE,
                                                                          DEFAULT_MAX_EVENT_STORAGE_DURATION,
                                                                          ClusterOpSpec.DEFAULT_CLUSTER_OP_TIMEOUT,
                                                                          ClusterOpSpec.DEFAULT_CLUSTER_OP_PARALLELISM,
                                                                          DEFAULT_JOB_RETRY_COUNT,
                                                                          DEFAULT_JOB_RETRY_INTERVAL,
                                                                          DEFAULT_INSTANCE_STATE_CHECK_RETRY_INTERVAL,
                                                                          DEFAULT_AUDITED_METHODS,
                                                                          DEFAULT_ALLOWED_MOUNT_DIRS,
                                                                          false,
                                                                          false,
                                                                          false);

    @MinDuration(value = 1, unit = TimeUnit.MINUTES)
    Duration staleCheckInterval;

    @MinDuration(value = 1, unit = TimeUnit.MINUTES)
    Duration staleAppAge;

    @Min(0)
    @Max(4096)
    int maxStaleInstancesCount;

    @MinDuration(value = 1, unit = TimeUnit.MINUTES)
    Duration staleInstanceAge;

    @MinDuration(value = 1, unit = TimeUnit.MINUTES)
    Duration staleTaskAge;

    @MinDuration(value = 1, unit = TimeUnit.MINUTES)
    Duration staleServiceAge;

    Duration maxEventsStorageDuration;

    @DurationRange(min = 10, max = 1_800, unit = TimeUnit.SECONDS)
    Duration clusterOpTimeout;

    @Range(max = 32)
    int clusterOpParallelism;

    @Range(min = 0, max = 32)
    Integer jobRetryCount;

    @DurationRange(min = 1, max = 1_800, unit = TimeUnit.SECONDS)
    Duration jobRetryInterval;

    @DurationRange(min = 5, max = 1_800, unit = TimeUnit.SECONDS)
    Duration instanceStateCheckRetryInterval;

    Set<String> auditedHttpMethods;

    List<String> allowedMountDirs;

    Boolean disableReadAuth;

    Boolean disableCmdlArgs;

    Boolean enableRawDeviceAccess;
}
