package com.phonepe.drove.controller.engine;

import com.phonepe.drove.common.ActionFactory;
import com.phonepe.drove.common.StateData;
import com.phonepe.drove.controller.statedb.ApplicationStateDB;
import com.phonepe.drove.controller.statemachine.AppAction;
import com.phonepe.drove.controller.statemachine.AppActionContext;
import com.phonepe.drove.controller.statemachine.ApplicationStateMachine;
import com.phonepe.drove.controller.utils.ControllerUtils;
import com.phonepe.drove.models.application.ApplicationInfo;
import com.phonepe.drove.models.application.ApplicationState;
import com.phonepe.drove.models.operation.ApplicationOperation;
import com.phonepe.drove.models.operation.ApplicationOperationType;
import com.phonepe.drove.models.operation.ApplicationOperationVisitorAdapter;
import com.phonepe.drove.models.operation.ClusterOpSpec;
import com.phonepe.drove.models.operation.ops.ApplicationCreateOperation;
import com.phonepe.drove.models.operation.ops.ApplicationScaleOperation;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 *
 */
@Singleton
@Slf4j
public class ApplicationEngine {
    private final Set<ApplicationState> OPERATION_ENABLED_STATES = EnumSet.of(ApplicationState.INIT,
                                                                              ApplicationState.MONITORING,
                                                                              ApplicationState.RUNNING);

    private final Map<String, ApplicationStateMachineExecutor> stateMachines = new ConcurrentHashMap<>();
    private final ActionFactory<ApplicationInfo, ApplicationOperation, ApplicationState, AppActionContext, AppAction> factory;
    private final ApplicationStateDB stateDB;
    private final ExecutorService monitorExecutor = Executors.newFixedThreadPool(1024);

    @Inject
    public ApplicationEngine(
            ActionFactory<ApplicationInfo, ApplicationOperation, ApplicationState, AppActionContext, AppAction> factory,
            ApplicationStateDB stateDB) {
        this.factory = factory;
        this.stateDB = stateDB;
    }

    public void handleOperation(final ApplicationOperation operation) {
        val appId = ControllerUtils.appId(operation);
        if (validateOp(appId, operation)) {
            stateMachines.computeIfAbsent(appId, id -> createApp(operation));
            stateMachines.computeIfPresent(appId, (id, monitor) -> {
                monitor.notifyUpdate(operation);
                return monitor;
            });
        }
        else {
            log.warn("Requested operation of type {} ignored for app {}", operation.getType().name(), appId);
        }
    }

    public void stopAll() {
        stateMachines.forEach((appId, exec) -> exec.stop());
        stateMachines.clear();
    }

    private boolean validateOp(String appId, ApplicationOperation operation) {
        //TODO::Check operation
        Objects.requireNonNull(operation, "Operation cannot be null");
/*        return applicationState(appId)
                .map(OPERATION_ENABLED_STATES::contains)
                .orElse(true);*/
        return true;
    }

    public Optional<ApplicationState> applicationState(final String appId) {
        return Optional.ofNullable(stateMachines.get(appId))
                .map(executor -> executor.getStateMachine().getCurrentState().getState());
    }

    private ApplicationStateMachineExecutor createApp(ApplicationOperation operation) {
        return operation.accept(new ApplicationOperationVisitorAdapter<>(null) {
            @Override
            public ApplicationStateMachineExecutor visit(ApplicationCreateOperation create) {
                val appSpec = create.getSpec();
                val now = new Date();
                val appId = ControllerUtils.appId(appSpec);
                val appInfo = new ApplicationInfo(appId, appSpec, create.getInstances(), now, now);
                val context = new AppActionContext(appId, appSpec);
                val stateMachine = new ApplicationStateMachine(StateData.create(
                        ApplicationState.INIT,
                        appInfo), context, factory);
                stateMachine.onStateChange().connect(newState -> handleAppStateUpdate(appId, context, newState));
                val monitor = new ApplicationStateMachineExecutor(
                        appId,
                        stateMachine,
                        monitorExecutor);
                monitor.start();
                return monitor;
            }
        });
    }

    private void handleAppStateUpdate(
            String appId,
            AppActionContext context,
            StateData<ApplicationState, ApplicationInfo> newState) {
        val state = newState.getState();
        log.info("App state: {}", state);
        if (state.equals(ApplicationState.SCALING_REQUESTED)) {
            val scalingOperation = context.getUpdate()
                    .filter(op -> op.getType().equals(ApplicationOperationType.SCALE))
                    .map(op -> {
                        val scaleOp = (ApplicationScaleOperation)op;
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
                                                      ClusterOpSpec.DEFAULT);
                    });
            handleOperation(scalingOperation);
        }
        else {
            if(state.equals(ApplicationState.DESTROYED)) {
                stateMachines.computeIfPresent(appId, (id, sm) -> {
                    sm.stop();
                    stateDB.deleteApplicationState(appId);
                    log.info("State machine stopped for: {}", appId);
                   return null;
                });
            }
        }
    }
}
