package com.phonepe.drove.controller.statemachine.actions;

import com.phonepe.drove.common.StateData;
import com.phonepe.drove.controller.engine.ControllerCommunicator;
import com.phonepe.drove.controller.engine.jobs.StopSingleInstanceJob;
import com.phonepe.drove.controller.jobexecutor.JobExecutionResult;
import com.phonepe.drove.controller.jobexecutor.JobExecutor;
import com.phonepe.drove.controller.jobexecutor.JobTopology;
import com.phonepe.drove.controller.resources.ClusterResourcesDB;
import com.phonepe.drove.controller.statedb.ApplicationStateDB;
import com.phonepe.drove.controller.statemachine.AppActionContext;
import com.phonepe.drove.controller.statemachine.AppAsyncAction;
import com.phonepe.drove.models.application.ApplicationInfo;
import com.phonepe.drove.models.application.ApplicationState;
import com.phonepe.drove.models.instance.InstanceInfo;
import com.phonepe.drove.models.operation.ApplicationOperation;
import com.phonepe.drove.models.operation.ops.ApplicationSuspendOperation;
import lombok.val;

import javax.inject.Inject;
import java.util.stream.Collectors;

import static com.phonepe.drove.controller.utils.ControllerUtils.safeCast;

/**
 *
 */
public class StopAppAction extends AppAsyncAction {
    private final ApplicationStateDB applicationStateDB;
    private final ClusterResourcesDB clusterResourcesDB;
    private final ControllerCommunicator communicator;

    @Inject
    public StopAppAction(
            JobExecutor<Boolean> jobExecutor,
            ApplicationStateDB applicationStateDB,
            ClusterResourcesDB clusterResourcesDB,
            ControllerCommunicator communicator) {
        super(jobExecutor);
        this.applicationStateDB = applicationStateDB;
        this.clusterResourcesDB = clusterResourcesDB;
        this.communicator = communicator;
    }

    @Override
    protected JobTopology<Boolean> jobsToRun(
            AppActionContext context,
            StateData<ApplicationState, ApplicationInfo> currentState,
            ApplicationOperation operation) {
        val suspendOperation = safeCast(operation, ApplicationSuspendOperation.class);
        val clusterOpSpec = suspendOperation.getOpSpec();
        val existingInstances = applicationStateDB.instances(
                suspendOperation.getAppId(), 0, Integer.MAX_VALUE);
        return JobTopology.<Boolean>builder()
                .addParallel(clusterOpSpec.getParallelism(), existingInstances
                        .stream()
                        .map(InstanceInfo::getInstanceId)
                        .map(iid -> new StopSingleInstanceJob(suspendOperation.getAppId(),
                                                              iid,
                                                              suspendOperation.getOpSpec(),
                                                              applicationStateDB,
                                                              clusterResourcesDB,
                                                              communicator))
                        .collect(Collectors.toUnmodifiableList()))
                .build();
    }

    @Override
    protected StateData<ApplicationState, ApplicationInfo> processResult(
            AppActionContext context,
            StateData<ApplicationState, ApplicationInfo> currentState,
            JobExecutionResult<Boolean> executionResult) {
        if(Boolean.TRUE.equals(executionResult.getResult())) {
            return StateData.from(currentState, ApplicationState.MONITORING);
        }
        return currentState;
    }
}
