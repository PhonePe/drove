/*
 *  Copyright (c) 2024 Original Author(s), PhonePe India Pvt. Ltd.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package com.phonepe.drove.models.operation;

import com.phonepe.drove.models.operation.deploy.FailureStrategy;
import io.dropwizard.util.Duration;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Value;
import lombok.With;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;

/**
 * Specification for cluster-wide operation settings including timeout and parallelism.
 */
@Value
@With
@Schema(description = "Specification for cluster-wide operation settings including timeout and parallelism")
public class ClusterOpSpec {
    public static final Duration DEFAULT_CLUSTER_OP_TIMEOUT = Duration.seconds(300);
    public static final int DEFAULT_CLUSTER_OP_PARALLELISM = 1;

    public static final ClusterOpSpec DEFAULT = new ClusterOpSpec(DEFAULT_CLUSTER_OP_TIMEOUT,
                                                                  DEFAULT_CLUSTER_OP_PARALLELISM,
                                                                  FailureStrategy.STOP);

    @NotNull
    @Schema(description = "Maximum time allowed for the operation to complete", example = "5 minutes", requiredMode = Schema.RequiredMode.REQUIRED)
    Duration timeout;

    @Min(1)
    @Max(32)
    @Schema(description = "Number of parallel operations to execute (1-32)", example = "1", minimum = "1", maximum = "32", defaultValue = "1")
    int parallelism;

    @NotNull
    @Schema(description = "Strategy to follow when an operation fails", example = "STOP", requiredMode = Schema.RequiredMode.REQUIRED)
    FailureStrategy failureStrategy;
}
