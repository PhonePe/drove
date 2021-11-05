package com.phonepe.drove.controller.jobexecutor;

/**
 *
 */
public class ErrorJob implements Job<Integer> {

    @Override
    public String jobId() {
        return "error-job";
    }

    @Override
    public void cancel() {

    }

    @Override
    public Integer execute(
            JobContext<Integer> context,
            JobResponseCombiner<Integer> responseCombiner) {
        throw new IllegalStateException("Error for testing");
    }
}
