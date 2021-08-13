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

    @Valid
    CheckModeSpec mode;

    @NotNull
    Duration timeout;

    @NotNull
    Duration interval;

    Duration initialDelay;
}
