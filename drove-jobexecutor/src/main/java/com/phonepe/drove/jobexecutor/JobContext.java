package com.phonepe.drove.jobexecutor;

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
    private final AtomicBoolean cancelled = new AtomicBoolean();
    @Setter
    @Getter
    private Future<JobExecutionResult<T>> future;

    public JobContext(Consumer<JobExecutionResult<T>> handler) {
        this.handler = handler;
    }

    public void markStopped() {
        stopped.set(true);
    }

    public void markCancelled() {
        cancelled.set(true);
    }

    public boolean isStopped() {
        return stopped.get();
    }

    public boolean isCancelled() {
        return cancelled.get();
    }
}
