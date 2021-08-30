package com.phonepe.drove.common;

import io.appform.signals.signals.ConsumingParallelSignal;
import lombok.Getter;
import lombok.NonNull;
import lombok.val;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 *
 */
public class StateMachine<T, S extends Enum<S>, C extends ActionContext, A extends Action<T, C, S>> {

    private final Map<S, Transition<T, S, C, A>> validTransitions;
    private final ConsumingParallelSignal<StateData<S, T>> stateChanged;

    @Getter
    private StateData<S, T> currentState;
    private final C context;
    private final AtomicReference<A> currentAction;

    protected StateMachine(
            @NonNull final StateData<S, T> initalState,
            C context,
            List<Transition<T, S, C, A>> transitions) {
        this.context = context;
        this.stateChanged = new ConsumingParallelSignal<>();
        this.validTransitions = transitions.stream()
                .collect(Collectors.toMap(Transition::getFrom, Function.identity()));
        this.currentState = initalState;
        currentAction = new AtomicReference<>();
    }

    public final ConsumingParallelSignal<StateData<S, T>> onStateChange() {
        return stateChanged;
    }

    public S execute() {
        val state = currentState.getState();
        val transition = validTransitions.get(state);
        currentAction.set(transition.getAction());
        val newStateData = currentAction.get().execute(context, currentState);
        val newState = newStateData.getState();
        if (!transition.getTo().contains(newState)) {
            throw new IllegalStateException("No defined transitions from " + state.name() + " to " + newState.name());
        }
        currentState = newStateData;
        stateChanged.dispatch(newStateData);
        return newState;
    }

    public void stop() {
        val action = currentAction.get();

        if(action != null) {
            action.stop(); //TODO::FIX the race condition here
        }
    }
}
