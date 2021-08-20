package com.phonepe.drove.common;

/**
 *
 */
public interface Action<T, C extends ActionContext, R extends Enum<R>> {
    StateData<R,T> execute(final C context, final StateData<R,T> currentState);

    void stop();
}
