package com.phonepe.drove.models.application;

import com.phonepe.drove.models.application.checks.CheckModeSpec;
import io.dropwizard.util.Duration;
import lombok.Value;

import javax.validation.Valid;
import java.util.List;

/**
 *
 */
@Value
public class PreShutdownSpec {
    public static final PreShutdownSpec DEFAULT = new PreShutdownSpec(List.of(), Duration.seconds(0));

    @Valid
    List<CheckModeSpec> hooks;

    Duration waitBeforeKill;
}
