package com.phonepe.drove.common;

/**
 *
 */
public interface Action<T, R extends Enum<R>, C extends ActionContext> {
    StateData<R,T> execute(final C context, final StateData<R,T> currentState);

    void stop();
}
