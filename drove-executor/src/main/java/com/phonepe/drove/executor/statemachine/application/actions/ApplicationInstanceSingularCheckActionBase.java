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

package com.phonepe.drove.executor.statemachine.application.actions;

import com.phonepe.drove.executor.checker.Checker;
import com.phonepe.drove.executor.statemachine.application.ApplicationInstanceAction;
import com.phonepe.drove.models.application.CheckResult;
import dev.failsafe.Failsafe;
import dev.failsafe.RetryPolicy;
import dev.failsafe.event.EventListener;
import dev.failsafe.event.ExecutionCompletedEvent;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 *
 */
@Slf4j
@NoArgsConstructor
public abstract class ApplicationInstanceSingularCheckActionBase extends ApplicationInstanceAction {
    protected final AtomicBoolean stop = new AtomicBoolean();

    @Override
    public final void stop() {
        stop.set(true);
    }

    @SuppressWarnings("java:S1874")
    protected final CheckResult checkWithRetry(
            RetryPolicy<CheckResult> retryPolicy,
            Checker checker,
            EventListener<ExecutionCompletedEvent<CheckResult>> errorConsumer) {
        return Failsafe.with(List.of(retryPolicy))
                .onComplete(errorConsumer)
                .get(() -> {
                    if (stop.get()) {
                        return CheckResult.stopped();
                    }
                    return checker.call();
                });
    }
}
