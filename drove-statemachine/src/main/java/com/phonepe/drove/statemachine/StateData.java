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

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Value;

/**
 * Current state of the system. Consists of some data that you can define as well as the states
 *
 * @param <S> An enum representing the different states of the state machine
 * @param <T> Type of the main data object for the SM
 */
@Value
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class StateData<S extends Enum<S>, T> {
    S state;
    T data;
    String error;

    /**
     * Create a new instance with data and state
     *
     * @param state Current state
     * @param data  Data for this state
     * @param <S>   State type enum
     * @param <T>   Type of data stored
     * @return A new instance of StateData
     */
    public static <S extends Enum<S>, T> StateData<S, T> create(S state, T data) {
        return create(state, data, "");
    }

    /**
     * Create StateData with an error message. To be used to expose out error in state machine action execution
     *
     * @param state Current state
     * @param data  Data for this state
     * @param error Error message for error that occurred while moving to this state
     * @param <S>   State type enum
     * @param <T>   Type of data stored
     * @return A new instance of StateData
     */
    public static <S extends Enum<S>, T> StateData<S, T> create(S state, T data, String error) {
        return new StateData<>(state, data, error);
    }

    /**
     * Create new state data from an old state data instance
     *
     * @param old   Old StateData instance
     * @param state New state system has moved to
     * @param <S>   State type enum
     * @param <T>   Type of data stored
     * @return A new instance of StateData
     */
    public static <S extends Enum<S>, T> StateData<S, T> from(StateData<S, T> old, S state) {
        return create(state, old.getData(), old.getError());
    }

    /**
     * Create error state from previous state data.
     *
     * @param old   Old StateData instance
     * @param state New state denoting error
     * @param error Error message (reason) for moving to this state
     * @param <S>   State type enum
     * @param <T>   Type of data stored
     * @return A new instance of StateData
     */
    public static <S extends Enum<S>, T> StateData<S, T> errorFrom(StateData<S, T> old, S state, String error) {
        return create(state, old.getData(), error);
    }

}
