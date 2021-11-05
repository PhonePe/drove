package com.phonepe.drove.controller.jobexecutor;

import java.util.concurrent.atomic.AtomicReference;

/**
 *
 */
public abstract class AbstractJobResponseCombiner<T> implements JobResponseCombiner<T> {
    protected final AtomicReference<Throwable> failure = new AtomicReference<>();

    @Override
    public boolean handleError(Throwable throwable) {
        failure.set(throwable);
        return false;
    }

}
