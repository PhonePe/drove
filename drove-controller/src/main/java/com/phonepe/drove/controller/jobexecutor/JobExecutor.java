package com.phonepe.drove.controller.jobexecutor;

import com.phonepe.drove.controller.utils.JobUtils;
import io.appform.signals.signals.ConsumingSyncSignal;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 *
 */
@Slf4j
public final class JobExecutor<T> {
    private final ExecutorService executorService;
    private final JobResponseCombiner<T> responseCombiner;
    private final ConsumingSyncSignal<JobExecutionResult<T>> jobCompleted = new ConsumingSyncSignal<>();
    private final ReentrantLock lock = new ReentrantLock();
    private final Condition completionCondition = lock.newCondition();
    private final Map<String, JobContext> contexts = new ConcurrentHashMap<>();

    public JobExecutor(ExecutorService executorService, JobResponseCombiner<T> responseCombiner) {
        this.executorService = executorService;
        this.responseCombiner = responseCombiner;
    }

    public ConsumingSyncSignal<JobExecutionResult<T>> onComplete() {
        return jobCompleted;
    }

    public String schedule(final List<Job<T>> jobs) {
        val id = JobUtils.idFromChildren(jobs);
        contexts.computeIfAbsent(id, k -> {
            val context = new JobContext();
            executorService.submit(() -> {
                for (Job<T> job : jobs) {
                    if (JobUtils.executeSingleJob(context, responseCombiner, job)) {
                        log.debug("Job {} is done. Moving on to next job.", job.jobId());
                    }
                    else {
                        break;
                    }
                }
                jobCompleted.dispatch(responseCombiner.buildResult());
            });
            return context;
        });
        return id;
    }

    public void cancel(String execId) {
        contexts.computeIfPresent(execId, (id, value) -> {
            value.markStopped();
            return value;
        });
    }

}
