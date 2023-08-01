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
    public static final Duration DEFAULT_CLUSTER_OP_TIMEOUT = Duration.seconds(300);
    public static final int DEFAULT_CLUSTER_OP_PARALLELISM = 1;

    @NotNull
    Duration timeout;

    @Min(1)
    @Max(32)
    int parallelism;

    @NotNull
    FailureStrategy failureStrategy;
}
