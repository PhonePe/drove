package com.phonepe.drove.models.application.checks;

import lombok.Value;

import javax.validation.constraints.NotNull;
import java.time.Duration;

/**
 *
 */
@Value
public class CheckRetryStrategySpec {
    @NotNull
    Duration interval;
    @NotNull
    Duration timeout;
}
