package com.phonepe.drove.controller.jobexecutor;

import java.util.concurrent.atomic.AtomicInteger;

/**
 *
 */
public class IntResponseCombiner extends AbstractJobResponseCombiner<Integer> {

    private final AtomicInteger current = new AtomicInteger();

    @Override
    public void combine(Job<Integer> job, Integer newResponse) {
        if(!job.isBatch()) {
            current.addAndGet(newResponse);
        }
    }

    @Override
    public Integer current() {
        return current.get();
    }

    @Override
    public JobExecutionResult<Integer> buildResult() {
        return new JobExecutionResult<>(current.get(), t.get());
    }
}
