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

package com.phonepe.drove.controller.statemachine.common.actions;

import com.phonepe.drove.statemachine.ActionContext;
import com.phonepe.drove.statemachine.StateData;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

/**
 * An {@link UnstoppableAction} that is driven by an operation, or example
 * {@link com.phonepe.drove.models.operation.ApplicationOperation} for managing applications
 */
@Slf4j
public abstract class OperationDrivenAction<T, S extends Enum<S>, C extends ActionContext<D>, D> extends UnstoppableAction<T, S, C, D> {
    @Override
    public final StateData<S, T> execute(
            C context, StateData<S, T> currentState) {
        val operation = context.getUpdate().orElse(null);
        if (null == operation) {
            log.warn("OperationDrivenAppAction triggered without any available operation. Returning to old state");
            return errorState(currentState, "No operation available");
        }
        try {
            return commandReceived(context, currentState, operation);
        }
        catch (Exception e) {
            log.error("Error occurred: ", e);
            return errorState(currentState, "Error: " + e.getMessage());
        }
        finally {
            log.debug("Acking operation: {}. Status: {}", operation, context.ackUpdate());
        }
    }

    protected abstract StateData<S, T> commandReceived(
            C context,
            StateData<S, T> currentState,
            D operation);

    protected abstract StateData<S, T> errorState(final StateData<S, T> currentState, String message);

}
