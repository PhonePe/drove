package com.phonepe.drove.common.discovery;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.time.Duration;

/**
 *
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class Constants {
    public static final Duration EXECUTOR_REFRESH_INTERVAL = Duration.ofSeconds(20);
}
