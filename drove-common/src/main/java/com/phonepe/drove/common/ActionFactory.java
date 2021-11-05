package com.phonepe.drove.common;

/**
 *
 */
public interface ActionFactory<T, D, R extends Enum<R>, C extends ActionContext<D>, A extends Action<T, R, C, D>> {
    A create(final Transition<T, D, R, C, A> transition);
}
