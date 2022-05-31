/*
 * Copyright 2022. Santanu Sinha
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in
 *   compliance with the License. You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is
 *  distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 *  express or implied. See the License for the specific language governing permissions and limitations
 *  under the License.
 */

package com.phonepe.drove.statemachine;

import lombok.Data;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * A context to hold state machine execution level data. It is upto implementors to figure out what members to add here.
 *
 * @param <D> Type of update to be passed to state machine
 */
@Data
public abstract class ActionContext<D> {
    private final AtomicBoolean alreadyStopped = new AtomicBoolean();

    private final AtomicReference<D> currentUpdate = new AtomicReference<>();

    /**
     * To be used by action and state machine to figure out if any update is present. Action can behave accordingly
     * if needed.
     *
     * @return The update if present or empty
     */
    public Optional<D> getUpdate() {
        return Optional.ofNullable(currentUpdate.get());
    }

    /**
     * Post an update to be consumed by action
     * @param update Data to be passed to action
     * @return Whether the update was recorded successfully or not
     */
    public boolean recordUpdate(D update) {
        return currentUpdate.compareAndSet(null, update);
    }

    /**
     * Action needs to call this to mark the update slot empty, so that further updates can be posted
     * @return Whether the update was ack-ed or not.
     */
    public boolean ackUpdate() {
        return currentUpdate.getAndSet(null) != null;
    }
}
