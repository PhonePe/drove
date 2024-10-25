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
import com.phonepe.drove.controller.statedb.ApplicationInstanceInfoDB;
import com.phonepe.drove.controller.statedb.ApplicationStateDB;
import com.phonepe.drove.controller.statemachine.applications.AppAction;
import com.phonepe.drove.controller.statemachine.applications.AppActionContext;
import com.phonepe.drove.controller.statemachine.applications.ApplicationStateMachine;
import com.phonepe.drove.controller.utils.ControllerUtils;
import com.phonepe.drove.models.application.ApplicationInfo;
import com.phonepe.drove.models.application.ApplicationState;
import com.phonepe.drove.models.events.events.DroveAppStateChangeEvent;
import com.phonepe.drove.models.instance.InstanceState;
import com.phonepe.drove.models.operation.ApplicationOperation;
import com.phonepe.drove.models.operation.ApplicationOperationType;
import com.phonepe.drove.models.operation.ApplicationOperationVisitorAdapter;
import com.phonepe.drove.models.operation.ClusterOpSpec;
import com.phonepe.drove.models.operation.ops.ApplicationCreateOperation;
import com.phonepe.drove.models.operation.ops.ApplicationScaleOperation;
import com.phonepe.drove.models.operation.ops.ApplicationStartInstancesOperation;
import com.phonepe.drove.models.operation.ops.ApplicationSuspendOperation;
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
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.function.BiConsumer;

import static com.phonepe.drove.controller.utils.EventUtils.appMetadata;

/**
 * Manages application lifecycle
 */
@Singleton
@Slf4j
public class ApplicationLifecycleManagentEngine extends DeployableLifeCycleManagementEngine<ApplicationInfo, ApplicationOperation,
        ApplicationState, AppActionContext, Action<ApplicationInfo, ApplicationState, AppActionContext,
        ApplicationOperation>> {

    private final ApplicationStateDB stateDB;
    private final ApplicationInstanceInfoDB instanceInfoDB;

    @Inject
    public ApplicationLifecycleManagentEngine(
            ActionFactory<ApplicationInfo, ApplicationOperation, ApplicationState, AppActionContext,
                    Action<ApplicationInfo, ApplicationState, AppActionContext, ApplicationOperation>> factory,
            ApplicationStateDB stateDB,
            ApplicationInstanceInfoDB instanceInfoDB,
            ApplicationCommandValidator applicationCommandValidator,
            DroveEventBus droveEventBus,
            ControllerRetrySpecFactory retrySpecFactory,
            @Named("MonitorThreadPool") ExecutorService monitorExecutor,
            ClusterOpSpec defaultClusterOpSpec) {
        super(factory, applicationCommandValidator, droveEventBus, retrySpecFactory, monitorExecutor, defaultClusterOpSpec);
        this.stateDB = stateDB;
    }

    @Override
    protected String deployableObjectId(ApplicationOperation operation) {
        return ControllerUtils.deployableObjectId(operation);
    }
 
    @Override
    protected ApplicationOperation translateOp(
            ApplicationOperation original,
            ClusterOpSpec defaultClusterOpSpec) {
        return original.accept(new ApplicationOperationVisitorAdapter<>(original) {
            @Override
            public ApplicationOperation visit(ApplicationStartInstancesOperation deploy) {
                val appId = deploy.getAppId();
                log.info("Translating deploy op to scaling op for {}", appId);
                val existing = instanceInfoDB.instanceCount(appId, InstanceState.HEALTHY);
                return new ApplicationScaleOperation(
                        appId, existing + deploy.getInstances(), deploy.getOpSpec());
            }

            @Override
            public ApplicationOperation visit(ApplicationSuspendOperation suspend) {
                val appId = suspend.getAppId();

                log.info("Translating suspend op to scaling op for {}", appId);
                return new ApplicationScaleOperation(
                        appId, 0, Objects.requireNonNullElse(suspend.getOpSpec(), defaultClusterOpSpec));
            }
        });
    }

    @Override
    protected StateMachineExecutor<ApplicationInfo, ApplicationOperation, ApplicationState, AppActionContext,
            Action<ApplicationInfo, ApplicationState, AppActionContext, ApplicationOperation>> createDeployable(
            ApplicationOperation operation,
            ActionFactory<ApplicationInfo, ApplicationOperation, ApplicationState, AppActionContext,
                    Action<ApplicationInfo, ApplicationState, AppActionContext, ApplicationOperation>> factory,
            ExecutorService monitorExecutor,
            ControllerRetrySpecFactory retrySpecFactory,
            BiConsumer<StateMachine<ApplicationInfo, ApplicationOperation, ApplicationState, AppActionContext,
                    Action<ApplicationInfo, ApplicationState, AppActionContext, ApplicationOperation>>,
                    AppActionContext> signalConnector) {
        return operation.accept(new ApplicationOperationVisitorAdapter<>(null) {
            @Override
            public ApplicationStateMachineExecutor visit(ApplicationCreateOperation create) {
                val appSpec = create.getSpec();
                val now = new Date();
                val appId = ControllerUtils.deployableObjectId(appSpec);
                val appInfo = new ApplicationInfo(appId, appSpec, create.getInstances(), now, now);
                val context = new AppActionContext(appId, appSpec);
                val stateMachine = new ApplicationStateMachine(StateData.create(
                        ApplicationState.INIT,
                        appInfo), context, factory);
                signalConnector.accept(stateMachine, context);
                val monitor = new ApplicationStateMachineExecutor(
                        appId,
                        stateMachine,
                        monitorExecutor,
                        retrySpecFactory,
                        stateMachineCompleted);
                //Record the update first then start the monitor
                // as the first thing it will do is look for the update
                if (!monitor.notifyUpdate(create)) {
                    log.error("Create operation could not be registered for app: {}", appId);
                }
                monitor.start();
                return monitor;
            }
        });
    }

    @Override
    protected void handleAppStateUpdate(
            String appId,
            AppActionContext context,
            StateData<ApplicationState, ApplicationInfo> newState,
            Map<String, StateMachineExecutor<ApplicationInfo, ApplicationOperation, ApplicationState, AppActionContext, Action<ApplicationInfo, ApplicationState, AppActionContext, ApplicationOperation>>> stateMachines,
            ClusterOpSpec defaultClusterOpSpec, DroveEventBus droveEventBus) {
        val state = newState.getState();
        log.info("App state: {}", state);
        if (state.equals(ApplicationState.SCALING_REQUESTED)) {
            val scalingOperation = context.getUpdate()
                    .filter(op -> op.getType().equals(ApplicationOperationType.SCALE_INSTANCES))
                    .map(op -> {
                        val scaleOp = (ApplicationScaleOperation) op;
                        stateDB.updateInstanceCount(scaleOp.getAppId(), scaleOp.getRequiredInstances());
                        log.info("App instances updated to: {}", scaleOp.getRequiredInstances());
                        return op;
                    })
                    .orElseGet(() -> {
                        val expectedInstances = stateDB.application(appId)
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
        else {
            if (state.equals(ApplicationState.DESTROYED)) {
                stateMachines.computeIfPresent(appId, (id, sm) -> {
                    stateDB.deleteApplicationState(appId);
                    instanceInfoDB.deleteAllInstancesForApp(appId);
                    log.info("Application state machine and instance data cleaned up for: {}", appId);
                    return null;
                });
            }
        }
        droveEventBus.publish(new DroveAppStateChangeEvent(appMetadata(appId, context.getApplicationSpec(), newState)));


    }


}
