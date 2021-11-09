package com.phonepe.drove.controller.engine;

import com.phonepe.drove.common.ActionFactory;
import com.phonepe.drove.common.StateData;
import com.phonepe.drove.controller.jobexecutor.JobExecutionResult;
import com.phonepe.drove.controller.jobexecutor.JobExecutor;
import com.phonepe.drove.controller.statedb.ApplicationStateDB;
import com.phonepe.drove.controller.statemachine.AppAction;
import com.phonepe.drove.controller.statemachine.AppActionContext;
import com.phonepe.drove.controller.statemachine.ApplicationStateMachine;
import com.phonepe.drove.controller.statemachine.ApplicationUpdateData;
import com.phonepe.drove.controller.utils.ControllerUtils;
import com.phonepe.drove.models.application.ApplicationInfo;
import com.phonepe.drove.models.application.ApplicationState;
import com.phonepe.drove.models.operation.ApplicationOperation;
import com.phonepe.drove.models.operation.ApplicationOperationVisitorAdapter;
import com.phonepe.drove.models.operation.ClusterOpSpec;
import com.phonepe.drove.models.operation.ops.ApplicationCreateOperation;
import com.phonepe.drove.models.operation.ops.ApplicationDeployOperation;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 *
 */
@Singleton
@Slf4j
public class ApplicationEngine {
    private final Map<String, ApplicationMonitor> stateMachines = new ConcurrentHashMap<>();
    private final ActionFactory<ApplicationInfo, ApplicationUpdateData, ApplicationState, AppActionContext, AppAction> factory;
    private final ApplicationStateDB stateDB;
    private final ExecutorService monitorExecutor = Executors.newFixedThreadPool(1024);

    @Inject
    public ApplicationEngine(
            ActionFactory<ApplicationInfo, ApplicationUpdateData, ApplicationState, AppActionContext, AppAction> factory,
            JobExecutor<Boolean> executor,
            ApplicationStateDB stateDB) {
        this.factory = factory;
        this.stateDB = stateDB;
        executor.onComplete().connect(this::handleJobCompleted);
    }

    public void handleOperation(final ApplicationOperation operation) {
        val appId = ControllerUtils.appId(operation);
        stateMachines.computeIfAbsent(appId, id -> createApp(operation));
        stateMachines.computeIfPresent(appId, (id, monitor) -> {
            monitor.notifyUpdate(new ApplicationUpdateData(operation, null));
            return monitor;
        });
    }


    private void handleJobCompleted(final JobExecutionResult<Boolean> result) {
        stateMachines.computeIfPresent(result.getJobId(), (id, sm) -> {
            sm.notifyUpdate(new ApplicationUpdateData(null, result));
            return sm;
        });
    }

    private ApplicationMonitor createApp(ApplicationOperation operation) {
        return operation.accept(new ApplicationOperationVisitorAdapter<>(null) {
            @Override
            public ApplicationMonitor visit(ApplicationCreateOperation create) {
                val appSpec = create.getSpec();
                val now = new Date();
                val appId = ControllerUtils.appId(appSpec);
                val appInfo = new ApplicationInfo(appId, appSpec, 0, now, now);
                val context = new AppActionContext(appId, appSpec);
                val stateMachine = new ApplicationStateMachine(StateData.create(
                        ApplicationState.INIT,
                        appInfo), context, factory);
                stateMachine.onStateChange().connect(newState -> handleAppStateUpdate(appId, newState));
                val monitor = new ApplicationMonitor(
                        appId,
                        stateMachine,
                        monitorExecutor);
                monitor.start();
                return monitor;
            }
        });
    }

    private void handleAppStateUpdate(String appId, StateData<ApplicationState, ApplicationInfo> newState) {
        log.info("App state: {}", newState.getState());
        switch (newState.getState()) {

            case INIT:
                break;
            case MONITORING:
                break;
            case DEPLOYMENT_REQUESTED:
                break;
            case RUNNING: {
                val runningInstances = stateDB.instanceCount(appId);
                if (stateDB.updateInstanceCount(appId, runningInstances)) {
                    log.info("Instance count has been updated to {}", runningInstances);
                }
                else {
                    log.warn("Instance count update failed.");
                }
                break;
            }
            case PARTIAL_OUTAGE: {
                val info = stateDB.application(appId).orElse(null);
                if (null == info) {
                    log.error("No app info found. Skipping update");
                    break;
                }
                val runningInstances = stateDB.instanceCount(appId);
                log.warn("Outage detected: Required {} Actual {}", info.getInstances(), runningInstances);
                if (info.getInstances() > runningInstances) {
                    stateMachines.computeIfPresent(appId, (id, sm) -> {
                        sm.notifyUpdate(new ApplicationUpdateData(new ApplicationDeployOperation(appId,
                                                                                                 info.getInstances() - runningInstances,
                                                                                                 ClusterOpSpec.DEFAULT),
                                                                  null));
                        return sm;
                    });
                }
                else {
//                    stateMachines.computeIfPresent(appId, (id, sm) -> {
//                        sm.notifyUpdate(new ApplicationS(new ApplicationDeployOperation(appId,
//                                                                                                 info.getInstances() - runningInstances,
//                                                                                                 ClusterOpSpec.DEFAULT),
//                                                                  null));
//                        return sm;
//                    });
                    //TODO::SHUTDOWN EXTRA INSTANCES
                }
                break;
            }
            case SUSPEND_REQUESTED:
                break;
            case SCALING_REQUESTED:
                break;
            case RESTART_REQUESTED:
                break;
            case DESTROY_REQUESTED:
                break;
            case DOWN:
                break;
            case SUSPENDED:
                break;
            case FAILED:
                break;
        }
    }
}
