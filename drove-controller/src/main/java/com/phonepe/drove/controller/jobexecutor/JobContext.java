package com.phonepe.drove.controller.jobexecutor;

import lombok.Getter;
import lombok.Setter;

import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 *
 */
public class JobContext<T> {
    @Getter
    private final Consumer<JobExecutionResult<T>> handler;
    private final AtomicBoolean stopped = new AtomicBoolean();
    @Setter
    @Getter
    private Future<JobExecutionResult<T>> future;

    public JobContext(Consumer<JobExecutionResult<T>> handler) {
        this.handler = handler;
    }

    public void markStopped() {
        stopped.set(true);
    }

    public boolean isStopped() {
        return stopped.get();
    }
}
