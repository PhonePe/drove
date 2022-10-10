package com.phonepe.drove.controller.config;

import io.dropwizard.util.Duration;
import lombok.Value;

/**
 *
 */
@Value
public class ControllerOptions {
    public static final Duration DEFAULT_STALE_CHECK_INTERVAL = Duration.hours(1);
    public static final Duration DEFAULT_STALE_APP_AGE = Duration.days(7);
    public static final Duration DEFAULT_STALE_INSTANCE_AGE = Duration.days(7);
    public static final Duration DEFAULT_STALE_TASK_AGE = Duration.days(2);
    public static final ControllerOptions DEFAULT = new ControllerOptions(DEFAULT_STALE_CHECK_INTERVAL,
                                                                          DEFAULT_STALE_APP_AGE,
                                                                          DEFAULT_STALE_INSTANCE_AGE,
                                                                          DEFAULT_STALE_TASK_AGE);

    Duration staleCheckInterval;
    Duration staleAppAge;
    Duration staleInstanceAge;
    Duration staleTaskAge;
}
