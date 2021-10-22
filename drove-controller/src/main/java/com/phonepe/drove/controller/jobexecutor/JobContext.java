package com.phonepe.drove.controller.jobexecutor;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 *
 */
public class JobContext {
    private final AtomicBoolean stopped = new AtomicBoolean();

    public void markStopped() {
        stopped.set(true);
    }

    public boolean isStopped() {
        return stopped.get();
    }
}
