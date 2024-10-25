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
import com.phonepe.drove.controller.statedb.LocalServiceStateDB;
import com.phonepe.drove.controller.statemachine.localservice.LocalServiceActionContext;
import com.phonepe.drove.controller.statemachine.localservice.LocalServiceAsyncAction;
import com.phonepe.drove.controller.statemachine.localservice.LocalServiceStateMachine;
import com.phonepe.drove.controller.utils.ControllerUtils;
import com.phonepe.drove.models.events.events.DroveLocalServiceStateChangeEvent;
import com.phonepe.drove.models.localservice.ActivationState;
import com.phonepe.drove.models.localservice.LocalServiceInfo;
import com.phonepe.drove.models.localservice.LocalServiceState;
import com.phonepe.drove.models.operation.ClusterOpSpec;
import com.phonepe.drove.models.operation.LocalServiceOperation;
import com.phonepe.drove.models.operation.LocalServiceOperationVisitorAdapter;
import com.phonepe.drove.models.operation.localserviceops.LocalServiceCreateOperation;
import com.phonepe.drove.statemachine.Action;
import com.phonepe.drove.statemachine.ActionFactory;
import com.phonepe.drove.statemachine.StateData;
import io.appform.functionmetrics.MonitoredFunction;
import io.appform.signals.signals.ConsumingFireForgetSignal;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import java.util.Date;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;

import static com.phonepe.drove.controller.utils.EventUtils.localServiceMetadata;

/**
 *
 */
@Singleton
@Slf4j
public class LocalServiceEngine {

    private final Map<String, LocalServiceStateMachineExecutor> stateMachines = new ConcurrentHashMap<>();
    private final ActionFactory<LocalServiceInfo, LocalServiceOperation, LocalServiceState, LocalServiceActionContext
            , Action<LocalServiceInfo, LocalServiceState, LocalServiceActionContext, LocalServiceOperation>> factory;
    private final LocalServiceStateDB stateDB;
    private final LocalServiceCommandValidator localServiceCommandValidator;
    private final DroveEventBus droveEventBus;
    private final ControllerRetrySpecFactory retrySpecFactory;

    private final ExecutorService monitorExecutor;
    private final ClusterOpSpec defaultClusterOpSpec;
    private final ConsumingFireForgetSignal<StateMachineExecutor<LocalServiceInfo, LocalServiceOperation,
            LocalServiceState, LocalServiceActionContext, Action<LocalServiceInfo, LocalServiceState,
            LocalServiceActionContext, LocalServiceOperation>>> stateMachineCompleted =
            new ConsumingFireForgetSignal<>();

    @Inject
    public LocalServiceEngine(
            ActionFactory<LocalServiceInfo, LocalServiceOperation, LocalServiceState, LocalServiceActionContext,
                    Action<LocalServiceInfo, LocalServiceState, LocalServiceActionContext, LocalServiceOperation>> factory,
            LocalServiceStateDB stateDB,
            LocalServiceCommandValidator localServiceCommandValidator,
            DroveEventBus droveEventBus,
            ControllerRetrySpecFactory retrySpecFactory,
            @Named("MonitorThreadPool") ExecutorService monitorExecutor,
            ClusterOpSpec defaultClusterOpSpec) {
        this.factory = factory;
        this.stateDB = stateDB;
        this.localServiceCommandValidator = localServiceCommandValidator;
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
    public ValidationResult handleOperation(final LocalServiceOperation operation) {
        val appId = ControllerUtils.deployableObjectId(operation);
        val res = validateOp(operation);
        if (res.getStatus().equals(ValidationStatus.SUCCESS)) {
            stateMachines.compute(appId, (id, monitor) -> {
                if (null == monitor) {
                    log.info("App {} is unknown. Going to create it now.", appId);
                    return createApp(operation);
                }
                if (!monitor.notifyUpdate(translateOp(operation))) {
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
    public boolean cancelCurrentJob(final String appId) {
        val sm = stateMachines.get(appId);
        if (null == sm) {
            return false;
        }
        val appSm = sm.getStateMachine();
        val action = (LocalServiceAsyncAction) appSm.currentAction()
                .filter(LocalServiceAsyncAction.class::isInstance)
                .orElse(null);
        if (null == action) {
            return false;
        }
        return action.cancel(appSm.getContext());
    }


    @MonitoredFunction
    public Optional<LocalServiceState> applicationState(final String appId) {
        return Optional.ofNullable(stateMachines.get(appId))
                .map(executor -> executor.getStateMachine().getCurrentState().getState());
    }

    boolean exists(final String appId) {
        return stateMachines.containsKey(appId);
    }

    private ValidationResult validateOp(final LocalServiceOperation operation) {
        Objects.requireNonNull(operation, "Operation cannot be null");
        return localServiceCommandValidator.validate(this, operation);
    }

    private LocalServiceOperation translateOp(final LocalServiceOperation original) {
        return original.accept(new LocalServiceOperationVisitorAdapter<LocalServiceOperation>(original) {
            //TODO::LOCAL_SERVICE
        });
    }

    private LocalServiceStateMachineExecutor createApp(LocalServiceOperation operation) {
        return operation.accept(new LocalServiceOperationVisitorAdapter<>(null) {
            @Override
            public LocalServiceStateMachineExecutor visit(LocalServiceCreateOperation create) {
                val lsSpec = create.getSpec();
                val now = new Date();
                val serviceId = ControllerUtils.deployableObjectId(lsSpec);
                val localServiceInfo = new LocalServiceInfo(serviceId, lsSpec, create.getInstancesPerHost(),
                                                   ActivationState.UNKNOWN, now, now);
                val context = new LocalServiceActionContext(serviceId, lsSpec);
                val stateMachine = new LocalServiceStateMachine(StateData.create(
                        LocalServiceState.INIT,
                        localServiceInfo), context, factory);
                stateMachine.onStateChange().connect(newState -> handleAppStateUpdate(serviceId, context, newState));
                val monitor = new LocalServiceStateMachineExecutor(
                        serviceId,
                        stateMachine,
                        monitorExecutor,
                        retrySpecFactory,
                        stateMachineCompleted);
                //Record the update first then start the monitor
                // as the first thing it will do is look for the update
                if (!monitor.notifyUpdate(create)) {
                    log.error("Create operation could not be registered for app: {}", serviceId);
                }
                monitor.start();
                return monitor;
            }
        });
    }

    private void handleAppStateUpdate(
            String serviceId,
            LocalServiceActionContext context,
            StateData<LocalServiceState, LocalServiceInfo> newState) {
        val state = newState.getState();
        log.info("Local Service state: {}", state);
/*        if (state.equals(ApplicationState.SCALING_REQUESTED)) { //TODO::LOCAL_SERVICE
            val scalingOperation = context.getUpdate()
                    .filter(op -> op.getType().equals(ApplicationOperationType.SCALE_INSTANCES))
                    .map(op -> {
                        val scaleOp = (ApplicationScaleOperation) op;
                        stateDB.updateInstanceCount(scaleOp.getAppId(), scaleOp.getRequiredInstances());
                        log.info("App instances updated to: {}", scaleOp.getRequiredInstances());
                        return op;
                    })
                    .orElseGet(() -> {
                        val expectedInstances = stateDB.application(serviceId)
                                .map(ApplicationInfo::getInstances)
                                .orElse(0L);
                        log.info("App is in scaling requested state. Setting appropriate operation to scale app to: {}",
                                 expectedInstances);
                        return new ApplicationScaleOperation(context.getAppId(),
                                                             expectedInstances,
                                                             defaultClusterOpSpec);
                    });
            log.debug("Scaling operation: {}", scalingOperation);
            val res = handleOperation(scalingOperation);
            if (!res.getStatus().equals(ValidationStatus.SUCCESS)) {
                log.error("Error sending command to state machine. Error: " + res.getMessages());
            }
        }
        else {*/
            if (state.equals(LocalServiceState.DESTROYED)) {
                stateMachines.computeIfPresent(serviceId, (id, sm) -> {
                    stateDB.removeService(serviceId);
                    stateDB.deleteAllInstancesForService(serviceId);
                    log.info("Local service state machine and instance data cleaned up for: {}", serviceId);
                    return null;
                });
            }
//        }
        droveEventBus.publish(new DroveLocalServiceStateChangeEvent(localServiceMetadata(serviceId, context.getLocalServiceSpec(), newState)));
    }
}
