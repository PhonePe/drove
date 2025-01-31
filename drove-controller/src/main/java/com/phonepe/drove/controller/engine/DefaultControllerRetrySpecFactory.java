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

package com.phonepe.drove.controller.engine;

import com.phonepe.drove.common.retry.*;
import com.phonepe.drove.controller.config.ControllerOptions;

import java.time.Duration;
import java.util.List;
import java.util.Objects;

/**
 *
 */
public class DefaultControllerRetrySpecFactory implements ControllerRetrySpecFactory {

    private final int numRetriesForJobExecution;
    private final Duration intervalBetweenJobRetry;

    private final Duration intervalBetweenInstanceStatusCheck;

    public DefaultControllerRetrySpecFactory() {
        this(ControllerOptions.DEFAULT);
    }

    public DefaultControllerRetrySpecFactory(ControllerOptions options) {
        numRetriesForJobExecution = Objects.requireNonNullElse(options.getJobRetryCount(),
                                                               ControllerOptions.DEFAULT_JOB_RETRY_COUNT);
        intervalBetweenJobRetry = Duration.ofMillis(
                Objects.requireNonNullElse(options.getJobRetryInterval(),
                                           ControllerOptions.DEFAULT_JOB_RETRY_INTERVAL).toMilliseconds());
        intervalBetweenInstanceStatusCheck = Duration.ofMillis(
                Objects.requireNonNullElse(options.getInstanceStateCheckRetryInterval(),
                                           ControllerOptions.DEFAULT_INSTANCE_STATE_CHECK_RETRY_INTERVAL)
                        .toMilliseconds());
    }

    @Override
    public RetrySpec jobRetrySpec(Duration expectedCompletionDuration) {
        return new CompositeRetrySpec(
                List.of(new RetryOnAllExceptionsSpec(),
                        new MaxDurationRetrySpec(Duration.ofMillis(expectedCompletionDuration.toMillis() * numRetriesForJobExecution)),
                        new IntervalRetrySpec(intervalBetweenJobRetry)));
    }

    @Override
    public RetrySpec jobRetrySpec() {
        return new CompositeRetrySpec(
                List.of(new RetryOnAllExceptionsSpec(),
                        new MaxRetriesRetrySpec(numRetriesForJobExecution),
                        new IntervalRetrySpec(intervalBetweenJobRetry)));
    }

    @Override
    public RetrySpec instanceStateCheckRetrySpec(long timeoutMillis) {
        return new CompositeRetrySpec(
                List.of(
                        new IntervalRetrySpec(intervalBetweenInstanceStatusCheck),
                        new MaxRetriesRetrySpec(-1),
                        new MaxDurationRetrySpec(Duration.ofMillis(timeoutMillis)),
                        new RetryOnAllExceptionsSpec()));
    }
}
