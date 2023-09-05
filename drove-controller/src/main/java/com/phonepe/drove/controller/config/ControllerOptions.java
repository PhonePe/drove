package com.phonepe.drove.controller.config;

import com.phonepe.drove.models.operation.ClusterOpSpec;
import io.dropwizard.util.Duration;
import io.dropwizard.validation.DurationRange;
import io.dropwizard.validation.MinDuration;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;
import org.hibernate.validator.constraints.Range;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import java.util.concurrent.TimeUnit;

/**
 *
 */
@Value
@Jacksonized
@Builder
@AllArgsConstructor
public class ControllerOptions {
    public static final Duration DEFAULT_STALE_CHECK_INTERVAL = Duration.hours(1);
    public static final Duration DEFAULT_STALE_APP_AGE = Duration.days(7);
    public static final int DEFAULT_MAX_STALE_INSTANCES_COUNT = 100;
    public static final Duration DEFAULT_STALE_INSTANCE_AGE = Duration.days(7);
    public static final Duration DEFAULT_STALE_TASK_AGE = Duration.days(2);
    public static final int DEFAULT_MAX_EVENTS_STORAGE_SIZE = 100;
    public static final Duration DEFAULT_MAX_EVENT_STORAGE_DURATION = Duration.minutes(60);

    public static final ControllerOptions DEFAULT = new ControllerOptions(DEFAULT_STALE_CHECK_INTERVAL,
                                                                          DEFAULT_STALE_APP_AGE,
                                                                          DEFAULT_MAX_STALE_INSTANCES_COUNT,
                                                                          DEFAULT_STALE_INSTANCE_AGE,
                                                                          DEFAULT_STALE_TASK_AGE,
                                                                          DEFAULT_MAX_EVENTS_STORAGE_SIZE,
                                                                          DEFAULT_MAX_EVENT_STORAGE_DURATION,
                                                                          ClusterOpSpec.DEFAULT_CLUSTER_OP_TIMEOUT,
                                                                          ClusterOpSpec.DEFAULT_CLUSTER_OP_PARALLELISM,
                                                                          true);

    @MinDuration(value = 1, unit = TimeUnit.MINUTES)
    Duration staleCheckInterval;

    @MinDuration(value = 1, unit = TimeUnit.MINUTES)
    Duration staleAppAge;

    /**
     * This parameter is now deprecated.
     * @deprecated Please use maxEventsStorageDuration instead
     */
    @Min(0)
    @Max(4096)
    @Deprecated
    int maxStaleInstancesCount;

    @MinDuration(value = 1, unit = TimeUnit.MINUTES)
    Duration staleInstanceAge;

    @MinDuration(value = 1, unit = TimeUnit.MINUTES)
    Duration staleTaskAge;

    @Min(0)
    @Max(2^20)
    int maxEventsStorageCount;

    @DurationRange(min = 10, max = 300, unit = TimeUnit.SECONDS)
    Duration clusterOpTimeout;

    @Range(max = 32)
    int clusterOpParallelism;

    Duration maxEventsStorageDuration;

    Boolean dieOnZkDisconnect;
}
