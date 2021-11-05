package com.phonepe.drove.controller.statemachine.actions;

import com.phonepe.drove.common.StateData;
import com.phonepe.drove.controller.engine.ControllerCommunicator;
import com.phonepe.drove.controller.engine.jobs.StartSingleInstanceJob;
import com.phonepe.drove.controller.jobexecutor.JobExecutionResult;
import com.phonepe.drove.controller.jobexecutor.JobExecutor;
import com.phonepe.drove.controller.jobexecutor.JobTopology;
import com.phonepe.drove.controller.resources.InstanceScheduler;
import com.phonepe.drove.controller.statedb.ApplicationStateDB;
import com.phonepe.drove.controller.statemachine.AppActionContext;
import com.phonepe.drove.controller.statemachine.AppAsyncAction;
import com.phonepe.drove.models.application.ApplicationInfo;
import com.phonepe.drove.models.application.ApplicationState;
import com.phonepe.drove.models.operation.ApplicationOperation;
import com.phonepe.drove.models.operation.ops.ApplicationDeployOperation;
import lombok.val;

import javax.inject.Inject;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static com.phonepe.drove.controller.utils.ControllerUtils.safeCast;

/**
 *
 */
public class StartAppAction extends AppAsyncAction {

    private final ApplicationStateDB applicationStateDB;
    private final InstanceScheduler scheduler;
    private final ControllerCommunicator communicator;

    @Inject
    public StartAppAction(
            ApplicationStateDB applicationStateDB,
            InstanceScheduler scheduler,
            ControllerCommunicator communicator,
            JobExecutor<Boolean> jobExecutor) {
        super(jobExecutor);
        this.applicationStateDB = applicationStateDB;
        this.scheduler = scheduler;
        this.communicator = communicator;
    }

    @Override
    protected JobTopology<Boolean> jobsToRun(
            AppActionContext context,
            StateData<ApplicationState, ApplicationInfo> currentState,
            ApplicationOperation operation) {
        val createOperation = safeCast(operation, ApplicationDeployOperation.class);
        val applicationSpec = context.getApplicationSpec();
        val clusterOpSpec = createOperation.getOpSpec();
        val parallelism = clusterOpSpec.getParallelism();
        return JobTopology.<Boolean>builder()
                .addParallel(parallelism, IntStream.range(0, applicationSpec.getInstances())
                        .mapToObj(i -> new StartSingleInstanceJob(applicationSpec,
                                                                  clusterOpSpec,
                                                                  scheduler,
                                                                  applicationStateDB,
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
            return StateData.from(currentState, ApplicationState.RUNNING);
        }
        return StateData.errorFrom(currentState, ApplicationState.FAILED, "Could not start application");
    }

}
