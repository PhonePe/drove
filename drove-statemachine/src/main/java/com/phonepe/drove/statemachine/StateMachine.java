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

import io.appform.functionmetrics.MonitoredFunction;
import io.appform.signals.signals.ConsumingSyncSignal;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * StateMachine representation for the system. A StateMachine has a core data that is modified by different actions.
 * State machine is defined by a set of valid transitions.
 *
 * @param <T> Type of the main data object for the SM
 * @param <D> Type of update to be passed to state machine
 * @param <S> An enum representing the different states of the state machine
 * @param <C> Actual derived type of the {@link ActionContext}. This will be passed to all actions and can be used to
 *            store some execution level data
 * @param <A> The actual base action type derived from {@link Action}. All actions in the state machine must derive
 *            from this base type.
 */
@Slf4j
public class StateMachine<T, D, S extends Enum<S>, C extends ActionContext<D>, A extends Action<T, S, C, D>> {

    private final Map<S, Transition<T, D, S, C, A>> validTransitions;
    private final ConsumingSyncSignal<StateData<S, T>> stateChanged;

    @Getter
    private StateData<S, T> currentState;
    @Getter
    private final C context;
    private final ActionFactory<T, D, S, C, A> actionFactory;
    private final AtomicReference<A> currentAction = new AtomicReference<>();

    /**
     * Constructor
     *
     * @param initialState   A non-null initial state for the state machine
     * @param context       A freshly minted context object that will be passed around to actions
     * @param actionFactory A {@link ActionFactory} implementation used to create actual {@link Action} derivatives
     *                      as ad when needed.
     * @param transitions   Set of transitions that define state movements in the state machine
     */
    protected StateMachine(
            final StateData<S, T> initialState,
            C context,
            ActionFactory<T, D, S, C, A> actionFactory,
            List<Transition<T, D, S, C, A>> transitions) {
        this.context = context;
        this.actionFactory = actionFactory;
        this.stateChanged = new ConsumingSyncSignal<>();
        this.validTransitions = transitions.stream()
                .collect(Collectors.toUnmodifiableMap(Transition::getFrom, Function.identity()));
        this.currentState = initialState;
    }

    /**
     * A signal that gets triggered every time the system undergoes a state transition. This can be used to attach
     * observers for audit logging, persistence etc.
     * <p>
     * Note: system will block till all observers return
     *
     * @return reference to the signal
     */
    public final ConsumingSyncSignal<StateData<S, T>> onStateChange() {
        return stateChanged;
    }

    /**
     * Execute the state machine.
     * NOTE: this will only execute one step of the state machine. You need to call this repeatedly in a loop or
     * something similar and use the returned state to stop the loop if needed.
     *
     * @return State after action related to out transition from current state is executed
     */
    @MonitoredFunction
    public S execute() {
        val state = currentState.getState();
        val transition = validTransitions.get(state);
        currentAction.set(actionFactory.create(transition));
        log.info("Action to be executed: {}", currentAction.get().getClass().getSimpleName());
        val newStateData = currentAction.get().execute(context, currentState);
        val newState = newStateData.getState();
        if (!transition.getTo().contains(newState)) {
            throw new IllegalStateException("No defined transitions from " + state.name() + " to " + newState.name());
        }
        currentState = newStateData;
        stateChanged.dispatch(newStateData);
        return newState;
    }

    /**
     * Get currently running action
     *
     * @return Current action if it exists or empty
     */
    public Optional<A> currentAction() {
        return Optional.ofNullable(currentAction.get());
    }

    /**
     * Post an update to be consumed by actions
     *
     * @param parameter The current update
     * @return If the update has been consumed successfully
     */
    public boolean notifyUpdate(D parameter) {
        val status = context.recordUpdate(parameter);
        if (status) {
            log.info("Update recorded successfully");
        }
        else {
            log.error("Update could not be recorded as there is already an update being processed. Current update: {}",
                      context.getUpdate().orElse(null));
        }
        return status;
    }

    /**
     * Stop any running actions
     */
    public void stop() {
        context.getAlreadyStopped().set(true);
        val action = currentAction.get();
        if (action != null) {
            action.stop();
        }
    }
}
