package com.phonepe.drove.controller.statemachine.actions;

import com.phonepe.drove.common.StateData;
import com.phonepe.drove.controller.engine.ControllerCommunicator;
import com.phonepe.drove.controller.engine.jobs.StartSingleInstanceJob;
import com.phonepe.drove.controller.engine.jobs.StopSingleInstanceJob;
import com.phonepe.drove.controller.jobexecutor.Job;
import com.phonepe.drove.controller.jobexecutor.JobExecutionResult;
import com.phonepe.drove.controller.jobexecutor.JobExecutor;
import com.phonepe.drove.controller.jobexecutor.JobTopology;
import com.phonepe.drove.controller.resourcemgmt.ClusterResourcesDB;
import com.phonepe.drove.controller.resourcemgmt.InstanceScheduler;
import com.phonepe.drove.controller.statedb.ApplicationStateDB;
import com.phonepe.drove.controller.statemachine.AppActionContext;
import com.phonepe.drove.controller.statemachine.AppAsyncAction;
import com.phonepe.drove.models.application.ApplicationInfo;
import com.phonepe.drove.models.application.ApplicationState;
import com.phonepe.drove.models.instance.InstanceState;
import com.phonepe.drove.models.operation.ApplicationOperation;
import com.phonepe.drove.models.operation.ops.ApplicationReplaceInstancesOperation;
import io.appform.functionmetrics.MonitoredFunction;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

import javax.inject.Inject;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import static com.phonepe.drove.controller.utils.ControllerUtils.safeCast;

/**
 *
 */
@Slf4j
public class ReplaceInstancesAppAction extends AppAsyncAction {
    private final ApplicationStateDB applicationStateDB;
    private final ClusterResourcesDB clusterResourcesDB;
    private final InstanceScheduler scheduler;
    private final ControllerCommunicator communicator;

    @Inject
    public ReplaceInstancesAppAction(
            JobExecutor<Boolean> jobExecutor,
            ApplicationStateDB applicationStateDB,
            ClusterResourcesDB clusterResourcesDB,
            InstanceScheduler scheduler, ControllerCommunicator communicator) {
        super(jobExecutor);
        this.applicationStateDB = applicationStateDB;
        this.clusterResourcesDB = clusterResourcesDB;
        this.scheduler = scheduler;
        this.communicator = communicator;
    }

    @Override
    @MonitoredFunction
    protected Optional<JobTopology<Boolean>> jobsToRun(
            AppActionContext context,
            StateData<ApplicationState, ApplicationInfo> currentState,
            ApplicationOperation operation) {
        val restartOp = safeCast(operation, ApplicationReplaceInstancesOperation.class);
        val appId = restartOp.getAppId();
        val instances = applicationStateDB.healthyInstances(restartOp.getAppId())
                .stream()
                .filter(instanceInfo -> (restartOp.getInstanceIds() == null || restartOp.getInstanceIds().isEmpty())
                                            || restartOp.getInstanceIds().contains(instanceInfo.getInstanceId()))
                .toList();
        val clusterOpSpec = restartOp.getOpSpec();
        val appSpec = applicationStateDB.application(appId).map(ApplicationInfo::getSpec).orElse(null);
        if (null == appSpec) {
            return Optional.empty();
        }
        int parallelism = clusterOpSpec.getParallelism();
        val schedulingSessionId = UUID.randomUUID().toString();
        log.info("{} instances to be restarted with parallelism: {}. Sched session ID: {}", instances.size(), parallelism, schedulingSessionId);
        val restartJobs = instances.stream()
                .map(instanceInfo -> (Job<Boolean>) JobTopology.<Boolean>builder()
                        .addJob(List.of(new StartSingleInstanceJob(appSpec,
                                                                   clusterOpSpec,
                                                                   scheduler,
                                                                   applicationStateDB,
                                                                   communicator,
                                                                   schedulingSessionId),
                                        new StopSingleInstanceJob(appId,
                                                                  instanceInfo.getInstanceId(),
                                                                  clusterOpSpec,
                                                                  applicationStateDB,
                                                                  clusterResourcesDB,
                                                                  communicator)))
                        .build())
                .toList();
        return Optional.of(JobTopology.<Boolean>builder()
                .addParallel(parallelism, restartJobs)
                .build());
    }

    @Override
    @MonitoredFunction
    protected StateData<ApplicationState, ApplicationInfo> processResult(
            AppActionContext context,
            StateData<ApplicationState, ApplicationInfo> currentState,
            ApplicationOperation operation,
            JobExecutionResult<Boolean> executionResult) {
        var errMsg = Boolean.TRUE.equals(executionResult.getResult())
                     ? ""
                     : (executionResult.getFailure() == null
                        ? "Execution failed"
                        : "Execution of jobs failed with error: " + executionResult.getFailure().getMessage());
        val count = applicationStateDB.instanceCount(context.getAppId(), InstanceState.HEALTHY);
        if (count > 0) {
            return StateData.errorFrom(currentState, ApplicationState.RUNNING, errMsg);
        }
        return StateData.errorFrom(currentState, ApplicationState.MONITORING, errMsg);
    }
}
