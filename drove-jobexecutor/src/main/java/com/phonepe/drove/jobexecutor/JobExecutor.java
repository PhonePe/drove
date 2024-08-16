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

package com.phonepe.drove.jobexecutor;

import io.appform.functionmetrics.MonitoredFunction;
import io.appform.signals.signals.ConsumingSyncSignal;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;

/**
 *
 */
@Slf4j
public final class JobExecutor<T> {
    private final ExecutorService executorService;
    private final ConsumingSyncSignal<JobExecutionResult<T>> jobCompleted = new ConsumingSyncSignal<>();
    private final Map<String, JobContext<T>> contexts = new ConcurrentHashMap<>();

    public JobExecutor(ExecutorService executorService) {
        this.executorService = executorService;
        this.jobCompleted.connect(r -> {
            log.debug("Cleaning up context for job: {}", r.getJobId());
            contexts.remove(r.getJobId());
        });
    }

    public ConsumingSyncSignal<JobExecutionResult<T>> onComplete() {
        return jobCompleted;
    }

    public String schedule(
            final JobTopology<T> job,
            final JobResponseCombiner<T> responseCombiner,
            final Consumer<JobExecutionResult<T>> resultHandler) {
        return schedule(Collections.singletonList(job), responseCombiner, resultHandler);
    }

    @MonitoredFunction
    public String schedule(
            final List<Job<T>> jobs,
            final JobResponseCombiner<T> responseCombiner,
            final Consumer<JobExecutionResult<T>> resultHandler) {
        val id = JobUtils.idFromChildren(jobs);
        contexts.computeIfAbsent(id, k -> {
            val context = new JobContext<>(resultHandler);
            val jobFuture = executorService.submit(() -> {
                for (val job : jobs) {
                    if (JobUtils.executeSingleJob(context, responseCombiner, job)) {
                        log.debug("Job {} is done. Moving on to next job.", job.jobId());
                    }
                    else {
                        break;
                    }
                }
                if(context.isCancelled()) {
                    responseCombiner.handleCancel();
                }
                val result = responseCombiner.buildResult(id);
                context.getHandler().accept(result);
                jobCompleted.dispatch(result);
                return result;
            });
            context.setFuture(jobFuture);
            return context;
        });
        return id;
    }

    public void cancel(String execId) {
        contexts.computeIfPresent(execId, (id, value) -> {
            value.markStopped();
            value.markCancelled();
            value.getFuture().cancel(false);
            return value;
        });
    }

}
