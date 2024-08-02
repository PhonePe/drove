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

/**
 * Action that will be taken, leading to state transition. Action returns the new state the system will go to. In case
 * if no transition, it should return the old state.
 * Note: Action instances will be created at runtime by {@link ActionFactory#create(Transition)} method. This will be
 * done every time a transition is encountered. Do not expect any kind of persistence. Any persistent data needs to
 * be stored in external storage or in {@link ActionContext}. Ideally do not let exceptions leak. Catch them and move
 * to appropriate state and set error message in the returned StateData.
 *
 * @param <T> Type of the main data object for the SM
 * @param <S> An enum representing the different states of the state machine
 * @param <C> Context type for the state machine
 * @param <D> Type of update to be passed to state machine
 */
public interface Action<T, S extends Enum<S>, C extends ActionContext<D>, D> {

    /**
     * Execute the required action which will move the state machine between states
     *
     * @param context      The context for this state machine execution
     * @param currentState Current state of the state machine
     * @return The new state the system should move to
     */
    StateData<S, T> execute(final C context, final StateData<S, T> currentState);

    /**
     * Stop the current action. It is upto implementer if the action is stoppable or not
     */
    void stop();
}
