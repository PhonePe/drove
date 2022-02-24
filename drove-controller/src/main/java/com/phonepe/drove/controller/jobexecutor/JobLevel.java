package com.phonepe.drove.controller.jobexecutor;

import com.phonepe.drove.controller.utils.JobUtils;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.IntStream;

/**
 *
 */
@Slf4j
final class JobLevel<T> implements Job<T> {
    private final String jobId;
    private final ExecutorService executorService;

    private final List<Job<T>> jobs;

    @SafeVarargs
    public JobLevel(int parallelism, Job<T>... jobs) {
        this(parallelism, Arrays.asList(jobs));
    }

    public JobLevel(
            int parallelism,
            List<Job<T>> jobs) {
        this.executorService = Executors.newFixedThreadPool(parallelism);
        this.jobs = jobs;
        jobId = JobUtils.idFromChildren(jobs);
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
        return responseCombiner.current();
    }

    @Override
    public boolean isBatch() {
        return true;
    }

    @Override
    public void cancel() {
        jobs.forEach(Job::cancel);
        executorService.shutdownNow();
    }
}
