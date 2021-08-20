package com.phonepe.drove.models.application.checks;

import io.dropwizard.util.Duration;
import lombok.Value;

import javax.validation.Valid;
import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;

/**
 *
 */
@Value
public class CheckSpec {

    @Valid
    CheckModeSpec mode;

    @NotNull
    Duration timeout;

    @NotNull
    Duration interval;

    @Min(1)
    @Max(100)
    int attempts;

    Duration initialDelay;
}
