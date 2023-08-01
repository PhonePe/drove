package com.phonepe.drove.controller.engine;

import com.phonepe.drove.controller.event.DroveEventBus;
import com.phonepe.drove.controller.statedb.ApplicationInstanceInfoDB;
import com.phonepe.drove.controller.statedb.ApplicationStateDB;
import com.phonepe.drove.controller.statemachine.applications.AppAction;
import com.phonepe.drove.controller.statemachine.applications.AppActionContext;
import com.phonepe.drove.controller.statemachine.applications.AppAsyncAction;
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

import static com.phonepe.drove.controller.utils.EventUtils.appMetadata;

/**
 *
 */
@Singleton
@Slf4j
public class ApplicationEngine {

    private final Map<String, ApplicationStateMachineExecutor> stateMachines = new ConcurrentHashMap<>();
    private final ActionFactory<ApplicationInfo, ApplicationOperation, ApplicationState, AppActionContext, AppAction> factory;
    private final ApplicationStateDB stateDB;
    private final ApplicationInstanceInfoDB instanceInfoDB;
    private final ApplicationCommandValidator applicationCommandValidator;
    private final DroveEventBus droveEventBus;
    private final ControllerRetrySpecFactory retrySpecFactory;

    private final ExecutorService monitorExecutor;
    private final ClusterOpSpec defaultClusterOpSpec;
    private final ConsumingFireForgetSignal<ApplicationStateMachineExecutor> stateMachineCompleted =
            new ConsumingFireForgetSignal<>();

    @Inject
    public ApplicationEngine(
            ActionFactory<ApplicationInfo, ApplicationOperation, ApplicationState, AppActionContext, AppAction> factory,
            ApplicationStateDB stateDB,
            ApplicationInstanceInfoDB instanceInfoDB,
            ApplicationCommandValidator applicationCommandValidator,
            DroveEventBus droveEventBus,
            ControllerRetrySpecFactory retrySpecFactory,
            @Named("MonitorThreadPool") ExecutorService monitorExecutor,
            ClusterOpSpec defaultClusterOpSpec) {
        this.factory = factory;
        this.stateDB = stateDB;
        this.instanceInfoDB = instanceInfoDB;
        this.applicationCommandValidator = applicationCommandValidator;
        this.droveEventBus = droveEventBus;
        this.retrySpecFactory = retrySpecFactory;
        this.monitorExecutor = monitorExecutor;
        this.defaultClusterOpSpec = defaultClusterOpSpec;
        this.stateMachineCompleted.connect(stoppedExecutor -> {
            stoppedExecutor.stop();
            log.info("State machine executor is done for {}", stoppedExecutor.getAppId());
        });
    }

    @MonitoredFunction
    public ValidationResult handleOperation(final ApplicationOperation operation) {
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
        val action = (AppAsyncAction) appSm.currentAction()
                .filter(a -> a instanceof AppAsyncAction)
                .orElse(null);
        if (null == action) {
            return false;
        }
        return action.cancel(appSm.getContext());
    }


    @MonitoredFunction
    public Optional<ApplicationState> applicationState(final String appId) {
        return Optional.ofNullable(stateMachines.get(appId))
                .map(executor -> executor.getStateMachine().getCurrentState().getState());
    }

    boolean exists(final String appId) {
        return stateMachines.containsKey(appId);
    }

    private ValidationResult validateOp(final ApplicationOperation operation) {
        Objects.requireNonNull(operation, "Operation cannot be null");
        return applicationCommandValidator.validate(this, operation);
    }

    private ApplicationOperation translateOp(final ApplicationOperation original) {
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

    private ApplicationStateMachineExecutor createApp(ApplicationOperation operation) {
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
                stateMachine.onStateChange().connect(newState -> handleAppStateUpdate(appId, context, newState));
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

    private void handleAppStateUpdate(
            String appId,
            AppActionContext context,
            StateData<ApplicationState, ApplicationInfo> newState) {
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
