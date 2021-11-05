package com.phonepe.drove.common;

import java.util.concurrent.atomic.AtomicReference;

/**
 *
 */
public abstract class Action<T, R extends Enum<R>, C extends ActionContext<D>, D> {

    private final AtomicReference<D> update = new AtomicReference<>();

    public abstract StateData<R,T> execute(final C context, final StateData<R, T> currentState);

    public void notifyUpdate(D updateValue) {
        update.compareAndExchange(null, updateValue);
    }

    public abstract void stop();
}
