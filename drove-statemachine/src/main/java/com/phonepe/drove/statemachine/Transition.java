/*
 *  Copyright (c) 2022 Original Author(s), PhonePe India Pvt. Ltd.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package com.phonepe.drove.statemachine;

import lombok.AllArgsConstructor;
import lombok.Value;

import java.util.Set;

/**
 * A valid transition in the state machine. The state machine will execute the specified {@link Action} and move to
 * one of the defined valid output states
 *
 * @param <T> Type of the main data object for the SM
 * @param <D> Type of update to be passed to state machine
 * @param <S> An enum representing the different states of the state machine
 * @param <C> Context type for the state machine
 * @param <A> The actual base action type derived from {@link Action}.
 */
@Value
@AllArgsConstructor
public class Transition<T, D, S extends Enum<S>, C extends ActionContext<D>, A extends Action<T, S, C, D>> {
    S from;
    Class<? extends A> action;
    Set<S> to;

    /**
     * Helper constructor
     * @param from State from which the transition will start
     * @param action Action to be invoked to generate next state
     * @param to Valid states that the action can generate
     */
    @SafeVarargs
    public Transition(S from, Class<? extends A> action, S... to) {
        this(from, action, Set.of(to));
    }
}
