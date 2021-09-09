package com.phonepe.drove.common;

/**
 *
 */
public interface ActionFactory<T, R extends Enum<R>, C extends ActionContext, A extends Action<T, R, C>> {
    A create(final Transition<T, R, C, A> transition);
}
