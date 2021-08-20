package com.phonepe.drove.common;

import lombok.EqualsAndHashCode;
import lombok.Value;

/**
 *
 */
@Value
public class TransitionedState<S extends Enum<S>> {
    S toState;
    @EqualsAndHashCode.Exclude
    boolean terminal;
}
