package com.phonepe.drove.common;

import io.appform.signals.signals.ConsumingParallelSignal;
import lombok.Getter;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 *
 */
@Slf4j
public class StateMachine<T, D, S extends Enum<S>, C extends ActionContext<D>, A extends Action<T, S, C, D>> {

    private final Map<S, Transition<T, D, S, C, A>> validTransitions;
    private final ConsumingParallelSignal<StateData<S, T>> stateChanged;

    @Getter
    private StateData<S, T> currentState;
    private final C context;
    private final ActionFactory<T, D, S, C, A> actionFactory;
    private final AtomicReference<A> currentAction = new AtomicReference<>();

    protected StateMachine(
            @NonNull final StateData<S, T> initalState,
            C context,
            ActionFactory<T, D, S,C,A> actionFactory,
            List<Transition<T, D, S, C, A>> transitions) {
        this.context = context;
        this.actionFactory = actionFactory;
        this.stateChanged = new ConsumingParallelSignal<>();
        this.validTransitions = transitions.stream()
                .collect(Collectors.toMap(Transition::getFrom, Function.identity()));
        this.currentState = initalState;
    }

    public final ConsumingParallelSignal<StateData<S, T>> onStateChange() {
        return stateChanged;
    }

    public S execute() {
        val state = currentState.getState();
        val transition = validTransitions.get(state);
        currentAction.set(actionFactory.create(transition));
        val newStateData = currentAction.get().execute(context, currentState);
        val newState = newStateData.getState();
        if (!transition.getTo().contains(newState)) {
            throw new IllegalStateException("No defined transitions from " + state.name() + " to " + newState.name());
        }
        currentState = newStateData;
        stateChanged.dispatch(newStateData);
        return newState;
    }

    public void notifyUpdate(D parameter) {
        if(context.recordUpdate(parameter)) {
            log.info("Update recorded successfully");
        }
        else {
            log.error("Update could not be recorded as there is already an update being processed");
        }
    }

    public void stop() {
        context.getAlreadyStopped().set(true);
        val action = currentAction.get();

        if(action != null) {
            action.stop(); //TODO::FIX the race condition here
        }
    }
}
