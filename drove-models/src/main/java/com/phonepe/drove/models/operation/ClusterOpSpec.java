package com.phonepe.drove.models.operation;

import com.phonepe.drove.models.operation.deploy.FailureStrategy;
import io.dropwizard.util.Duration;
import lombok.Value;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;

/**
 *
 */
@Value
public class ClusterOpSpec {


    @NotNull
    Duration timeout;

    @Min(1)
    @Max(32)
    int parallelism;

    @NotNull
    FailureStrategy failureStrategy;
}
