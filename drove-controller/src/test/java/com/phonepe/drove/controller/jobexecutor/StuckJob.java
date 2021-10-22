package com.phonepe.drove.controller.jobexecutor;

/**
 *
 */
public class StuckJob implements Job<Integer> {

    @Override
    public String jobId() {
        return "stuck-job";
    }

    @Override
    public void cancel() {

    }

    @Override
    public Integer execute(
            JobContext context,
            JobResponseCombiner<Integer> responseCombiner) {
        while (!context.isStopped());
        return 0;
    }
}
