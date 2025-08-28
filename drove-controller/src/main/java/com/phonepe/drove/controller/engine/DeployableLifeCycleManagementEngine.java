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

package com.phonepe.drove.controller.engine;

import com.phonepe.drove.controller.event.DroveEventBus;
import com.phonepe.drove.controller.statemachine.common.actions.UnstoppableAction;
import com.phonepe.drove.models.operation.ClusterOpSpec;
import com.phonepe.drove.statemachine.*;
import io.appform.functionmetrics.MonitoredFunction;
import io.appform.signals.signals.ConsumingFireForgetSignal;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

import javax.inject.Named;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.function.BiConsumer;

/**
 * Base class to manage the lifecycle for application and local service lifecycles
 */
@Slf4j
public abstract class DeployableLifeCycleManagementEngine<T, V, D, S extends Enum<S>, C extends ActionContext<D>,
        A extends Action<T, S, C, D>> {

    private final Map<String, StateMachineExecutor<T, D, S, C, A>> stateMachines = new ConcurrentHashMap<>();
    private final ActionFactory<T, D, S, C, A> factory;

    private final CommandValidator<D, V, DeployableLifeCycleManagementEngine<T, V, D, S, C, A>> commandValidator;
    private final DroveEventBus droveEventBus;
    private final ControllerRetrySpecFactory retrySpecFactory;

    private final ExecutorService monitorExecutor;
    private final ClusterOpSpec defaultClusterOpSpec;
    protected final ConsumingFireForgetSignal<StateMachineExecutor<T, D, S, C, A>> stateMachineCompleted
            = new ConsumingFireForgetSignal<>();

    protected DeployableLifeCycleManagementEngine(
            ActionFactory<T, D, S, C, A> factory,
            CommandValidator<D, V, DeployableLifeCycleManagementEngine<T, V, D, S, C, A>> commandValidator,
            DroveEventBus droveEventBus,
            ControllerRetrySpecFactory retrySpecFactory,
            @Named("MonitorThreadPool") ExecutorService monitorExecutor,
            ClusterOpSpec defaultClusterOpSpec) {
        this.factory = factory;
        this.commandValidator = commandValidator;
        this.droveEventBus = droveEventBus;
        this.retrySpecFactory = retrySpecFactory;
        this.monitorExecutor = monitorExecutor;
        this.defaultClusterOpSpec = defaultClusterOpSpec;
        this.stateMachineCompleted.connect(stoppedExecutor -> {
            stoppedExecutor.stop();
            log.info("State machine executor is done for {}", stoppedExecutor.getDeployableId());
        });
    }

    @MonitoredFunction
    public ValidationResult validateSpec(final V spec) {
        return commandValidator.validateSpec(spec);
    }

    @MonitoredFunction
    public ValidationResult handleOperation(final D operation) {
        val deployableObjectId = deployableObjectId(operation);
        val res = validateOp(operation);
        if (res.getStatus().equals(ValidationStatus.SUCCESS)) {
            stateMachines.compute(deployableObjectId, (id, monitor) -> {
                if (null == monitor) {
                    log.info("Deployable {} is unknown. Going to create it now.", deployableObjectId);
                    return createDeployable(
                            operation,
                            factory,
                            monitorExecutor,
                            retrySpecFactory,
                            (stateMachine, context) -> stateMachine
                                    .onStateChange()
                                    .connect(newState -> handleStateUpdate(deployableObjectId,
                                                                           context,
                                                                           newState,
                                                                           stateMachines,
                                                                           defaultClusterOpSpec,
                                                                           droveEventBus)));

                }
                if (!monitor.notifyUpdate(translateOp(operation, defaultClusterOpSpec))) {
                    log.warn("Update could not be sent");
                }
                return monitor;
            });
        }
        return res;
    }

    @MonitoredFunction
    public void stopAll() {
        stateMachines.forEach((appId, exec) -> exec.stop());
        stateMachines.clear();
    }

    @MonitoredFunction
    @SuppressWarnings("unchecked")
    public boolean cancelCurrentJob(final String deployableId) {
        val sm = stateMachines.get(deployableId);
        if (null == sm) {
            return false;
        }
        val stateMachine = sm.getStateMachine();
        val currentAction = stateMachine.currentAction().orElse(null);
        if (null != currentAction && UnstoppableAction.class.isAssignableFrom(currentAction.getClass())) {
            return ((UnstoppableAction<T,S,C,D>) currentAction).cancel(stateMachine.getContext());
        }
        return false;
    }


    @MonitoredFunction
    public Optional<S> currentState(final String appId) {
        return Optional.ofNullable(stateMachines.get(appId))
                .map(executor -> executor.getStateMachine().getCurrentState().getState());
    }

    protected abstract String deployableObjectId(D operation);

    boolean exists(final String appId) {
        return stateMachines.containsKey(appId);
    }

    private ValidationResult validateOp(final D operation) {
        Objects.requireNonNull(operation, "Operation cannot be null");
        return commandValidator.validateOperation(this, operation);
    }

    protected abstract D translateOp(final D original, ClusterOpSpec defaultClusterOpSpec);

    protected abstract StateMachineExecutor<T, D, S, C, A> createDeployable(
            D operation,
            ActionFactory<T, D, S, C, A> factory,
            ExecutorService monitorExecutor,
            ControllerRetrySpecFactory retrySpecFactory,
            BiConsumer<StateMachine<T, D, S, C, A>, C> signalConnector);

    protected abstract void handleStateUpdate(
            String deployableId,
            C context,
            StateData<S, T> newState,
            Map<String, StateMachineExecutor<T, D, S, C, A>> stateMachines,
            ClusterOpSpec defaultClusterOpSpec, DroveEventBus eventBus);
}
