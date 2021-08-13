package com.phonepe.drove.models.application.checks;

import io.dropwizard.util.Duration;
import lombok.Value;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;

/**
 *
 */
@Value
public class CheckSpec {
    @NotNull
    Duration timeout;

    @Valid
    CheckModeSpec mode;

    @Valid
    CheckRetryStrategySpec retry;
}
