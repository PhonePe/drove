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

import lombok.extern.slf4j.Slf4j;
import lombok.val;

import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.stream.IntStream;

/**
 *
 */
@Slf4j
final class JobLevel<T> implements Job<T> {
    private final String jobId;
    private final ExecutorService executorService;
    private final List<Job<T>> jobs;

    public JobLevel(int parallelism, List<Job<T>> jobs) {
        this(parallelism, Executors.defaultThreadFactory(), jobs);
    }

    public JobLevel(int parallelism, ThreadFactory threadFactory, List<Job<T>> jobs) {
        this.executorService = Executors.newFixedThreadPool(parallelism, threadFactory);
        this.jobs = jobs;
        this.jobId = JobUtils.idFromChildren(jobs);
    }

    @Override
    public String jobId() {
        return jobId;
    }

    @Override
    public T execute(JobContext<T> context, final JobResponseCombiner<T> responseCombiner) {
        val workList = List.copyOf(this.jobs);
        val futures = workList.stream()
                .map(job -> executorService.submit(() -> JobUtils.executeSingleJob(context, responseCombiner, job)))
                .toList();

        IntStream.range(0, futures.size())
                .forEach(i -> {
                    try {
                        futures.get(i).get();
                    }
                    catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    catch (ExecutionException e) {
                        log.error("Error running job: " + workList.get(i).jobId(), e);
                    }
                });
        if(!executorService.isShutdown()) {
            executorService.shutdownNow();
        }
        return responseCombiner.current();
    }

    @Override
    public boolean isBatch() {
        return true;
    }

    @Override
    public void cancel() {
        if(!executorService.isShutdown()) {
            jobs.forEach(Job::cancel);
            executorService.shutdownNow();
        }
    }
}
