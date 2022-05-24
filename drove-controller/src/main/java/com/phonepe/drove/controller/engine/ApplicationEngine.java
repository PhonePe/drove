package com.phonepe.drove.controller.engine;

import com.phonepe.drove.common.model.utils.Pair;
import com.phonepe.drove.controller.event.DroveEventBus;
import com.phonepe.drove.controller.event.events.DroveAppStateChangeEvent;
import com.phonepe.drove.controller.statedb.ApplicationStateDB;
import com.phonepe.drove.controller.statedb.InstanceInfoDB;
import com.phonepe.drove.controller.statemachine.AppAction;
import com.phonepe.drove.controller.statemachine.AppActionContext;
import com.phonepe.drove.controller.statemachine.AppAsyncAction;
import com.phonepe.drove.controller.statemachine.ApplicationStateMachine;
import com.phonepe.drove.controller.utils.ControllerUtils;
import com.phonepe.drove.models.application.ApplicationInfo;
import com.phonepe.drove.models.application.ApplicationState;
import com.phonepe.drove.models.instance.InstanceState;
import com.phonepe.drove.models.operation.ApplicationOperation;
import com.phonepe.drove.models.operation.ApplicationOperationType;
import com.phonepe.drove.models.operation.ApplicationOperationVisitorAdapter;
import com.phonepe.drove.models.operation.ClusterOpSpec;
import com.phonepe.drove.models.operation.ops.*;
import io.appform.functionmetrics.MonitoredFunction;
import io.appform.signals.signals.ConsumingFireForgetSignal;
import io.appform.simplefsm.ActionFactory;
import io.appform.simplefsm.StateData;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Date;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

/**
 *
 */
@Singleton
@Slf4j
public class ApplicationEngine {

    private final Map<String, ApplicationStateMachineExecutor> stateMachines = new ConcurrentHashMap<>();
    private final ActionFactory<ApplicationInfo, ApplicationOperation, ApplicationState, AppActionContext, AppAction> factory;
    private final ApplicationStateDB stateDB;
    private final InstanceInfoDB instanceInfoDB;
    private final CommandValidator commandValidator;
    private final DroveEventBus droveEventBus;
    private final ControllerRetrySpecFactory retrySpecFactory;

    private final ExecutorService monitorExecutor = Executors.newFixedThreadPool(1024);
    private final ConsumingFireForgetSignal<ApplicationStateMachineExecutor> stateMachineCompleted = ConsumingFireForgetSignal.<ApplicationStateMachineExecutor>builder()
            .executorService(monitorExecutor)
            .build();

    @Inject
    public ApplicationEngine(
            ActionFactory<ApplicationInfo, ApplicationOperation, ApplicationState, AppActionContext, AppAction> factory,
            ApplicationStateDB stateDB,
            InstanceInfoDB instanceInfoDB,
            CommandValidator commandValidator,
            DroveEventBus droveEventBus,
            ControllerRetrySpecFactory retrySpecFactory) {
        this.factory = factory;
        this.stateDB = stateDB;
        this.instanceInfoDB = instanceInfoDB;
        this.commandValidator = commandValidator;
        this.droveEventBus = droveEventBus;
        this.retrySpecFactory = retrySpecFactory;
        this.stateMachineCompleted.connect(stoppedExecutor -> {
            stoppedExecutor.stop();
            log.info("State machine executor is done for {}", stoppedExecutor.getAppId());
        });
    }

    @MonitoredFunction
    public CommandValidator.ValidationResult handleOperation(final ApplicationOperation operation) {
        val appId = ControllerUtils.appId(operation);
        val res = validateOp(operation);
        if (res.getStatus().equals(CommandValidator.ValidationStatus.SUCCESS)) {
            stateMachines.computeIfAbsent(appId, id -> createApp(operation));
            stateMachines.computeIfPresent(appId, (id, monitor) -> {
                if(!monitor.notifyUpdate(translateOp(operation))) {
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
        if(null == sm) {
            return false;
        }
        val appSm = sm.getStateMachine();
        val action = (AppAsyncAction) appSm.currentAction()
                .filter(a -> a instanceof AppAsyncAction)
                .orElse(null);
        if(null == action) {
            return false;
        }
        return action.cancel(appSm.getContext());
    }


    @MonitoredFunction
    public Optional<ApplicationState> applicationState(final String appId) {
        return Optional.ofNullable(stateMachines.get(appId))
                .map(executor -> executor.getStateMachine().getCurrentState().getState());
    }

    @MonitoredFunction
    public void moveInstancesFromExecutor(final String executorId) {
        stateDB.applications(0, Integer.MAX_VALUE)
                .stream()
                .flatMap(app -> instanceInfoDB.healthyInstances(app.getAppId()).stream())
                .filter(instanceInfo -> instanceInfo.getExecutorId().equals(executorId))
                .map(instanceInfo -> new Pair<>(instanceInfo.getAppId(), instanceInfo.getInstanceId()))
                .collect(Collectors.groupingBy(Pair::getFirst, Collectors.mapping(Pair::getSecond, Collectors.toUnmodifiableSet())))
                .forEach((appId, instances) -> {
                    val res = handleOperation(new ApplicationReplaceInstancesOperation(appId, instances, ClusterOpSpec.DEFAULT));
                    log.info("Instances to be replaced for {}: {}. command acceptance status: {}", appId, instances, res);
                });
    }

    boolean exists(final String appId) {
        return stateMachines.containsKey(appId);
    }

    private CommandValidator.ValidationResult validateOp(final ApplicationOperation operation) {
        //TODO::Check operation
        Objects.requireNonNull(operation, "Operation cannot be null");
        return commandValidator.validate(this, operation);
    }

    private ApplicationOperation translateOp(final ApplicationOperation original) {
        return original.accept(new ApplicationOperationVisitorAdapter<>(original) {
            @Override
            public ApplicationOperation visit(ApplicationStartInstancesOperation deploy) {
                val appId = deploy.getAppId();
                log.info("Translating deploy op to scaling op for {}", appId);
                val existing = instanceInfoDB.instanceCount(appId, InstanceState.HEALTHY);
                return new ApplicationScaleOperation(appId,
                                                     existing + deploy.getInstances(),
                                                     deploy.getOpSpec());
            }

            @Override
            public ApplicationOperation visit(ApplicationSuspendOperation suspend) {
                val appId = suspend.getAppId();

                log.info("Translating suspend op to scaling op for {}", appId);
                return new ApplicationScaleOperation(appId, 0, suspend.getOpSpec());
            }
        });
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
                        monitorExecutor,
                        retrySpecFactory,
                        stateMachineCompleted);
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
                                                             ClusterOpSpec.DEFAULT);
                    });
            val res = handleOperation(scalingOperation);
            if (!res.getStatus().equals(CommandValidator.ValidationStatus.SUCCESS)) {
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
        droveEventBus.publish(new DroveAppStateChangeEvent(appId, newState.getState()));
    }
}
