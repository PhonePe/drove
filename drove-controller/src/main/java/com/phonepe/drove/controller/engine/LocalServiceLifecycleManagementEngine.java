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
import com.phonepe.drove.models.operation.localserviceops.LocalServiceReplaceInstancesOperation;
import com.phonepe.drove.models.operation.localserviceops.LocalServiceRestartOperation;
import com.phonepe.drove.statemachine.Action;
import com.phonepe.drove.statemachine.ActionFactory;
import com.phonepe.drove.statemachine.StateData;
import com.phonepe.drove.statemachine.StateMachine;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import java.util.Date;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.function.BiConsumer;

import static com.phonepe.drove.controller.utils.EventUtils.localServiceMetadata;

/**
 * Manages local service lifecycle
 */
@Singleton
@Slf4j
public class LocalServiceLifecycleManagementEngine extends DeployableLifeCycleManagementEngine<LocalServiceInfo,
        LocalServiceOperation, LocalServiceState, LocalServiceActionContext, Action<LocalServiceInfo,
        LocalServiceState, LocalServiceActionContext, LocalServiceOperation>> {

    private final LocalServiceStateDB stateDB;

    @Inject
    public LocalServiceLifecycleManagementEngine(
            ActionFactory<LocalServiceInfo, LocalServiceOperation, LocalServiceState, LocalServiceActionContext,
                    Action<LocalServiceInfo, LocalServiceState, LocalServiceActionContext, LocalServiceOperation>> factory,
            LocalServiceStateDB stateDB,
            LocalServiceCommandValidator localServiceCommandValidator,
            DroveEventBus droveEventBus,
            ControllerRetrySpecFactory retrySpecFactory,
            @Named("MonitorThreadPool") ExecutorService monitorExecutor,
            ClusterOpSpec defaultClusterOpSpec) {
        super(factory,
              localServiceCommandValidator,
              droveEventBus,
              retrySpecFactory,
              monitorExecutor,
              defaultClusterOpSpec);
        this.stateDB = stateDB;
    }

    @Override
    protected String deployableObjectId(LocalServiceOperation operation) {
        return ControllerUtils.deployableObjectId(operation);
    }

    @Override
    protected LocalServiceOperation translateOp(
            LocalServiceOperation original,
            ClusterOpSpec defaultClusterOpSpec) {
        return original.accept(new LocalServiceOperationVisitorAdapter<>(original) {
            @Override
            public LocalServiceOperation visit(LocalServiceRestartOperation localServiceRestartOperation) {
                return new LocalServiceReplaceInstancesOperation(localServiceRestartOperation.getServiceId(),
                                                                 Set.of(),
                                                                 localServiceRestartOperation.isStopFirst(),
                                                                 localServiceRestartOperation.getClusterOpSpec());
            }
        });
    }

    @Override
    protected StateMachineExecutor<LocalServiceInfo, LocalServiceOperation, LocalServiceState,
            LocalServiceActionContext, Action<LocalServiceInfo, LocalServiceState, LocalServiceActionContext,
            LocalServiceOperation>> createDeployable(
            LocalServiceOperation operation,
            ActionFactory<LocalServiceInfo, LocalServiceOperation, LocalServiceState, LocalServiceActionContext,
                    Action<LocalServiceInfo, LocalServiceState, LocalServiceActionContext, LocalServiceOperation>> factory,
            ExecutorService monitorExecutor,
            ControllerRetrySpecFactory retrySpecFactory,
            BiConsumer<StateMachine<LocalServiceInfo, LocalServiceOperation, LocalServiceState,
                    LocalServiceActionContext, Action<LocalServiceInfo, LocalServiceState, LocalServiceActionContext,
                    LocalServiceOperation>>,
                    LocalServiceActionContext> signalConnector) {
        return operation.accept(new LocalServiceOperationVisitorAdapter<LocalServiceStateMachineExecutor>(null) {
            @Override
            public LocalServiceStateMachineExecutor visit(LocalServiceCreateOperation create) {
                val serviceSpec = create.getSpec();
                val now = new Date();
                val serviceId = ControllerUtils.deployableObjectId(serviceSpec);
                val appInfo = new LocalServiceInfo(serviceId,
                                                   serviceSpec,
                                                   create.getInstancesPerHost(),
                                                   ActivationState.INACTIVE,
                                                   now,
                                                   now);
                val context = new LocalServiceActionContext(serviceId, serviceSpec);
                val stateMachine = new LocalServiceStateMachine(StateData.create(
                        LocalServiceState.INIT,
                        appInfo), context, factory);
                signalConnector.accept(stateMachine, context);
                val monitor = new LocalServiceStateMachineExecutor(
                        serviceId,
                        stateMachine,
                        monitorExecutor,
                        retrySpecFactory,
                        stateMachineCompleted);
                //Record the update first then start the monitor
                // as the first thing it will do is look for the update
                if (!monitor.notifyUpdate(create)) {
                    log.error("Create operation could not be registered for local service: {}", serviceId);
                }
                monitor.start();
                return monitor;
            }
        });
    }

    @Override
    protected void handleStateUpdate(
            String serviceId,
            LocalServiceActionContext context,
            StateData<LocalServiceState, LocalServiceInfo> newState,
            Map<String, StateMachineExecutor<LocalServiceInfo, LocalServiceOperation, LocalServiceState,
                    LocalServiceActionContext, Action<LocalServiceInfo, LocalServiceState, LocalServiceActionContext,
                    LocalServiceOperation>>> stateMachines,
            ClusterOpSpec defaultClusterOpSpec, DroveEventBus droveEventBus) {
        val state = newState.getState();
        log.info("Local Service state: {}", state);
        if (/*state.equals(LocalServiceState.ACTIVE) || */state.equals(LocalServiceState.INACTIVE)) {
/*            val res = handleOperation(new LocalServiceAdjustInstancesOperation(serviceId));
            if (!res.getStatus().equals(ValidationStatus.SUCCESS)) {
                log.error("Error sending command to state machine. Error: " + res.getMessages());
            }*/
        }
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
        droveEventBus.publish(new DroveLocalServiceStateChangeEvent(localServiceMetadata(serviceId,
                                                                                         context.getLocalServiceSpec(),
                                                                                         newState)));


    }


}
