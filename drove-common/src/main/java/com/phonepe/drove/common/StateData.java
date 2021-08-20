package com.phonepe.drove.common;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Value;

/**
 *
 */
@Value
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class StateData<S extends Enum<S>, T> {
    S state;
    T data;
    String error;

    public static<S extends Enum<S>, T> StateData<S, T> create(S state, T data) {
        return create(state, data, "");
    }

    public static<S extends Enum<S>, T> StateData<S, T> create(S state, T data, String error) {
        return new StateData<>(state, data, error);
    }

    public static <S extends Enum<S>, T> StateData<S,T> errorFrom(StateData<S,T> old, S state, String error) {
        return create(state, old.getData(), error);
    }

    public static <S extends Enum<S>, T> StateData<S,T> from(StateData<S,T> old, S state) {
        return create(state, old.getData(), old.error);
    }
}
