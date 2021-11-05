package com.phonepe.drove.controller.engine;

import com.phonepe.drove.common.ActionFactory;
import com.phonepe.drove.common.StateData;
import com.phonepe.drove.controller.jobexecutor.JobExecutionResult;
import com.phonepe.drove.controller.jobexecutor.JobExecutor;
import com.phonepe.drove.controller.statemachine.*;
import com.phonepe.drove.controller.utils.ControllerUtils;
import com.phonepe.drove.models.application.ApplicationInfo;
import com.phonepe.drove.models.application.ApplicationState;
import com.phonepe.drove.models.operation.ApplicationOperation;
import com.phonepe.drove.models.operation.ApplicationOperationVisitorAdapter;
import com.phonepe.drove.models.operation.ops.ApplicationCreateOperation;
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
public class ApplicationEngine {
    private final Map<String, ApplicationMonitor> stateMachines = new ConcurrentHashMap<>();
    private final ActionFactory<ApplicationInfo, ApplicationUpdateData, ApplicationState, AppActionContext, AppAction> factory;
    private final ExecutorService monitorExecutor = Executors.newFixedThreadPool(1024);

    @Inject
    public ApplicationEngine(
            ActionFactory<ApplicationInfo, ApplicationUpdateData, ApplicationState, AppActionContext, AppAction> factory,
            JobExecutor<Boolean> executor) {
        this.factory = factory;
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
                val appInfo = new ApplicationInfo(appId, appSpec, now, now);
                val context = new AppActionContext(appSpec);
                val monitor = new ApplicationMonitor(
                        appId,
                        new ApplicationStateMachine(StateData.create(ApplicationState.INIT, appInfo), context, factory),
                        monitorExecutor);
                monitor.start();
                return monitor;
            }
        });
    }


}
