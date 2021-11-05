package com.phonepe.drove.controller.jobexecutor;

import lombok.SneakyThrows;

import java.security.SecureRandom;
import java.util.Date;
import java.util.Random;

/**
 *
 */
public final class Adder implements Job<Integer> {
    private static final Random RANDOM = new SecureRandom();
    private final int i;
    private final long currTime = new Date().getTime();

    Adder(int i) {
        this.i = i;
    }

    @Override
    public String jobId() {
        return "adder" + i + "-" + currTime;
    }

    @Override
    public void cancel() {

    }

    @SneakyThrows
    @Override
    public Integer execute(
            JobContext<Integer> context,
            final JobResponseCombiner<Integer> responseCombiner) {
        Thread.sleep(RANDOM.nextInt(100));
        return i;
    }
}
