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

package com.phonepe.drove.controller.engine.jobs;

import com.phonepe.drove.jobexecutor.AbstractJobResponseCombiner;
import com.phonepe.drove.jobexecutor.Job;
import com.phonepe.drove.jobexecutor.JobExecutionResult;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * This will mimic an OR combiner
 */
public class BooleanResponseCombiner extends AbstractJobResponseCombiner<Boolean> {
    private final AtomicBoolean current = new AtomicBoolean();

    @Override
    public void combine(Job<Boolean> job, Boolean newResponse) {
        current.compareAndSet(false, newResponse);
    }

    @Override
    public Boolean current() {
        return current.get();
    }

    @Override
    public JobExecutionResult<Boolean> buildResult(String jobId) {
        return new JobExecutionResult<>(jobId, current(), super.failure.get(), super.cancelled.get());
    }
}
