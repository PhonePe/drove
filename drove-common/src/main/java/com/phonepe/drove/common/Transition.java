package com.phonepe.drove.common;

import lombok.AllArgsConstructor;
import lombok.Value;

import java.util.Set;

/**
 *
 */
@Value
@AllArgsConstructor
public class Transition<T, S extends Enum<S>, C extends ActionContext, A extends Action<T, C, S>> {
    S from;
    A action;
    Set<S> to;

    @SafeVarargs
    public Transition(S from, A action, S... to) {
        this(from, action, Set.of(to));
    }
}
