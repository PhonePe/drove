package com.phonepe.drove.controller.statemachine.applications.actions;

import com.phonepe.drove.controller.engine.ControllerCommunicator;
import com.phonepe.drove.controller.engine.ControllerRetrySpecFactory;
import com.phonepe.drove.controller.engine.jobs.StopSingleInstanceJob;
import com.phonepe.drove.jobexecutor.Job;
import com.phonepe.drove.jobexecutor.JobExecutionResult;
import com.phonepe.drove.jobexecutor.JobExecutor;
import com.phonepe.drove.jobexecutor.JobTopology;
import com.phonepe.drove.controller.resourcemgmt.ClusterResourcesDB;
import com.phonepe.drove.controller.statedb.ApplicationStateDB;
import com.phonepe.drove.controller.statedb.ApplicationInstanceInfoDB;
import com.phonepe.drove.controller.statemachine.applications.AppActionContext;
import com.phonepe.drove.controller.statemachine.applications.AppAsyncAction;
import com.phonepe.drove.models.application.ApplicationInfo;
import com.phonepe.drove.models.application.ApplicationState;
import com.phonepe.drove.models.instance.InstanceState;
import com.phonepe.drove.models.operation.ApplicationOperation;
import com.phonepe.drove.models.operation.ops.ApplicationStopInstancesOperation;
import io.appform.functionmetrics.MonitoredFunction;
import com.phonepe.drove.statemachine.StateData;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

import javax.inject.Inject;
import javax.inject.Named;
import java.util.Optional;
import java.util.concurrent.ThreadFactory;

import static com.phonepe.drove.controller.utils.ControllerUtils.safeCast;

/**
 *
 */
@Slf4j
public class StopAppInstancesAction extends AppAsyncAction {
    private final ApplicationStateDB applicationStateDB;
    private final ApplicationInstanceInfoDB instanceInfoDB;
    private final ClusterResourcesDB clusterResourcesDB;
    private final ControllerCommunicator communicator;
    private final ControllerRetrySpecFactory retrySpecFactory;
    private final ThreadFactory threadFactory;

    @Inject
    public StopAppInstancesAction(
            final JobExecutor<Boolean> jobExecutor,
            final ApplicationStateDB applicationStateDB,
            ApplicationInstanceInfoDB instanceInfoDB,
            final ClusterResourcesDB clusterResourcesDB,
            final ControllerCommunicator communicator,
            ControllerRetrySpecFactory retrySpecFactory,
            @Named("JobLevelThreadFactory") ThreadFactory threadFactory) {
        super(jobExecutor, instanceInfoDB);
        this.applicationStateDB = applicationStateDB;
        this.instanceInfoDB = instanceInfoDB;
        this.clusterResourcesDB = clusterResourcesDB;
        this.communicator = communicator;
        this.retrySpecFactory = retrySpecFactory;
        this.threadFactory = threadFactory;
    }

    @Override
    @MonitoredFunction
    protected Optional<JobTopology<Boolean>> jobsToRun(
            AppActionContext context,
            StateData<ApplicationState, ApplicationInfo> currentState,
            ApplicationOperation operation) {
        val stopAction = safeCast(operation, ApplicationStopInstancesOperation.class);
        return Optional.of(JobTopology.<Boolean>builder()
                                   .withThreadFactory(threadFactory)
                .addParallel(stopAction.getOpSpec().getParallelism(), stopAction.getInstanceIds()
                        .stream()
                        .map(instanceId -> (Job<Boolean>)new StopSingleInstanceJob(stopAction.getAppId(),
                                                                                   instanceId,
                                                                                   stopAction.getOpSpec(),
                                                                                   instanceInfoDB,
                                                                                   clusterResourcesDB,
                                                                                   communicator,
                                                                                   retrySpecFactory))
                        .toList())
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
            val stopAction = safeCast(operation, ApplicationStopInstancesOperation.class);
            val appId = stopAction.getAppId();
            val instanceIds = stopAction.getInstanceIds();
            if (stopAction.isSkipRespawn()) {

                val currCount = applicationStateDB.application(appId).map(ApplicationInfo::getInstances).orElse(0L);
                val requested = (currCount - instanceIds.size()) < 0 ? 0 : (currCount - instanceIds.size());
                applicationStateDB.updateInstanceCount(appId, requested);
                log.info("Respawn skip has been specified. App {} instances will not be scaled up. Old count: {} New count: {}",
                         appId, currCount, requested);
                if(requested == 0) {
                    return StateData.from(currentState, ApplicationState.MONITORING);
                }
            }

            return StateData.from(currentState, ApplicationState.RUNNING);
        }
        val errorMessage = null == executionResult.getFailure()
                           ? "Could not stop application instances"
                           : "Could not stop application instances: " + executionResult.getFailure().getMessage();

        if (instanceInfoDB.instanceCount(context.getAppId(), InstanceState.HEALTHY) > 0) {
            return StateData.errorFrom(currentState, ApplicationState.RUNNING, errorMessage);
        }
        return StateData.errorFrom(currentState, ApplicationState.MONITORING, errorMessage);
    }
}
