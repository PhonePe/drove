package com.phonepe.drove.controller.statemachine.actions;

import com.phonepe.drove.common.StateData;
import com.phonepe.drove.controller.engine.ControllerCommunicator;
import com.phonepe.drove.controller.engine.jobs.StopSingleInstanceJob;
import com.phonepe.drove.controller.jobexecutor.JobExecutionResult;
import com.phonepe.drove.controller.jobexecutor.JobExecutor;
import com.phonepe.drove.controller.jobexecutor.JobTopology;
import com.phonepe.drove.controller.resourcemgmt.ClusterResourcesDB;
import com.phonepe.drove.controller.statedb.ApplicationStateDB;
import com.phonepe.drove.controller.statemachine.AppActionContext;
import com.phonepe.drove.controller.statemachine.AppAsyncAction;
import com.phonepe.drove.models.application.ApplicationInfo;
import com.phonepe.drove.models.application.ApplicationState;
import com.phonepe.drove.models.instance.InstanceState;
import com.phonepe.drove.models.operation.ApplicationOperation;
import com.phonepe.drove.models.operation.ops.ApplicationStopInstancesOperation;
import io.appform.functionmetrics.MonitoredFunction;
import lombok.val;

import javax.inject.Inject;

import java.util.Optional;
import java.util.stream.Collectors;

import static com.phonepe.drove.controller.utils.ControllerUtils.safeCast;

/**
 *
 */
public class StopAppInstancesAction extends AppAsyncAction {
    private final ApplicationStateDB applicationStateDB;
    private final ClusterResourcesDB clusterResourcesDB;
    private final ControllerCommunicator communicator;

    @Inject
    public StopAppInstancesAction(
            final JobExecutor<Boolean> jobExecutor,
            final ApplicationStateDB applicationStateDB,
            final ClusterResourcesDB clusterResourcesDB,
            final ControllerCommunicator communicator) {
        super(jobExecutor);
        this.applicationStateDB = applicationStateDB;
        this.clusterResourcesDB = clusterResourcesDB;
        this.communicator = communicator;
    }

    @Override
    @MonitoredFunction
    protected Optional<JobTopology<Boolean>> jobsToRun(
            AppActionContext context,
            StateData<ApplicationState, ApplicationInfo> currentState,
            ApplicationOperation operation) {
        val stopAction = safeCast(operation, ApplicationStopInstancesOperation.class);
        return Optional.of(JobTopology.<Boolean>builder()
                .addParallel(stopAction.getOpSpec().getParallelism(), stopAction.getInstanceIds()
                        .stream()
                        .map(instanceId -> new StopSingleInstanceJob(stopAction.getAppId(),
                                                                     instanceId,
                                                                     stopAction.getOpSpec(),
                                                                     applicationStateDB,
                                                                     clusterResourcesDB,
                                                                     communicator))
                        .collect(Collectors.toUnmodifiableList()))
                .build());
    }

    @Override
    @MonitoredFunction
    protected StateData<ApplicationState, ApplicationInfo> processResult(
            AppActionContext context,
            StateData<ApplicationState, ApplicationInfo> currentState,
            ApplicationOperation operation,
            JobExecutionResult<Boolean> executionResult) {
        if (Boolean.TRUE.equals(executionResult.getResult())) {
            return StateData.from(currentState, ApplicationState.RUNNING);
        }
        val errorMessage = null == executionResult.getFailure()
                           ? "Could not stop application instances"
                           : "Could not stop application instances: " + executionResult.getFailure().getMessage();

        if (applicationStateDB.instanceCount(context.getAppId(), InstanceState.HEALTHY) > 0) {
            return StateData.errorFrom(currentState, ApplicationState.RUNNING, errorMessage);
        }
        return StateData.errorFrom(currentState, ApplicationState.MONITORING, errorMessage);
    }
}
