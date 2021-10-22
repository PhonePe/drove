package com.phonepe.drove.controller.jobexecutor;

import java.util.concurrent.atomic.AtomicReference;

/**
 *
 */
public abstract class AbstractJobResponseCombiner<T> implements JobResponseCombiner<T> {
    protected final AtomicReference<Throwable> t = new AtomicReference<>();

    @Override
    public boolean handleError(Throwable throwable) {
        t.set(throwable);
        return false;
    }

}
