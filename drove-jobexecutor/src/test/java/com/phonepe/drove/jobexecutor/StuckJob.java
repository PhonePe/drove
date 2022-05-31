package com.phonepe.drove.jobexecutor;

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
            JobContext<Integer> context,
            JobResponseCombiner<Integer> responseCombiner) {
        while (!context.isStopped());
        return 0;
    }
}
