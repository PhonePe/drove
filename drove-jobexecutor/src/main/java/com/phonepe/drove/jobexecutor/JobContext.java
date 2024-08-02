/*
 *  Copyright (c) 2022 Original Author(s), PhonePe India Pvt. Ltd.
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

package com.phonepe.drove.jobexecutor;

import lombok.Getter;
import lombok.Setter;

import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 *
 */
public class JobContext<T> {
    @Getter
    private final Consumer<JobExecutionResult<T>> handler;
    private final AtomicBoolean stopped = new AtomicBoolean();
    private final AtomicBoolean cancelled = new AtomicBoolean();
    @Setter
    @Getter
    private Future<JobExecutionResult<T>> future;

    public JobContext(Consumer<JobExecutionResult<T>> handler) {
        this.handler = handler;
    }

    public void markStopped() {
        stopped.set(true);
    }

    public void markCancelled() {
        cancelled.set(true);
    }

    public boolean isStopped() {
        return stopped.get();
    }

    public boolean isCancelled() {
        return cancelled.get();
    }
}
