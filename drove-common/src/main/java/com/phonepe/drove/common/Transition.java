package com.phonepe.drove.common;

import lombok.AllArgsConstructor;
import lombok.Value;

import java.util.Set;

/**
 *
 */
@Value
@AllArgsConstructor
public class Transition<T, D, S extends Enum<S>, C extends ActionContext<D>, A extends Action<T, S, C, D>> {
    S from;
    Class<? extends A> action;
    Set<S> to;

    @SafeVarargs
    public Transition(S from, Class<? extends A> action, S... to) {
        this(from, action, Set.of(to));
    }
}
