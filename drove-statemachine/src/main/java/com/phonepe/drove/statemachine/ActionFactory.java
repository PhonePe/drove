/*
 *  Copyright (c) 2024 Original Author(s), PhonePe India Pvt. Ltd.
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

/**
 * Generates an {@link Action} instance at runtime.
 *
 * Note: If actions need parameters, DI system needs to be used in the create method
 *
 * @param <T> Type of the main data object for the SM
 * @param <D> Type of update to be passed to state machine
 * @param <S> An enum representing the different states of the state machine
 * @param <C> Context type for the state machine
 * @param <A> The actual base action type derived from {@link Action}.
 * */
public interface ActionFactory<T, D, S extends Enum<S>, C extends ActionContext<D>, A extends Action<T, S, C, D>> {

    /**
     * Crates an instance of appropriate Action
     * @param transition Transition for which Action will be created. Class name for action is specified in {@link Transition}
     * @return A freshly minted action instance
     */
    A create(final Transition<T, D, S, C, A> transition);
}
