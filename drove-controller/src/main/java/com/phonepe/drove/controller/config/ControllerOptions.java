package com.phonepe.drove.controller.config;

import io.dropwizard.util.Duration;
import lombok.Value;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;

/**
 *
 */
@Value
public class ControllerOptions {
    public static final Duration DEFAULT_STALE_CHECK_INTERVAL = Duration.hours(1);
    public static final Duration DEFAULT_STALE_APP_AGE = Duration.days(7);
    public static final int DEFAULT_MAX_STALE_INSTANCES_COUNT = 100;
    public static final Duration DEFAULT_STALE_INSTANCE_AGE = Duration.days(7);
    public static final Duration DEFAULT_STALE_TASK_AGE = Duration.days(2);
    public static final int DEFAULT_MAX_EVENTS_STORAGE_SIZE = 100;

    public static final ControllerOptions DEFAULT = new ControllerOptions(DEFAULT_STALE_CHECK_INTERVAL,
                                                                          DEFAULT_STALE_APP_AGE,
                                                                          DEFAULT_MAX_STALE_INSTANCES_COUNT,
                                                                          DEFAULT_STALE_INSTANCE_AGE,
                                                                          DEFAULT_STALE_TASK_AGE,
                                                                          DEFAULT_MAX_EVENTS_STORAGE_SIZE,
                                                                          false);

    Duration staleCheckInterval;
    Duration staleAppAge;

    @Min(0)
    @Max(4096)
    int maxStaleInstancesCount;
    Duration staleInstanceAge;
    Duration staleTaskAge;
    @Min(0)
    @Max(1024)
    int maxEventsStorageCount;

    Boolean dieOnZkDisconnect;
}
