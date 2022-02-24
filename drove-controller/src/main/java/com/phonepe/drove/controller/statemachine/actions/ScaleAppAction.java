package com.phonepe.drove.controller.statemachine.actions;

import com.google.common.base.Strings;
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
import com.phonepe.drove.models.instance.InstanceInfo;
import com.phonepe.drove.models.instance.InstanceState;
import com.phonepe.drove.models.operation.ApplicationOperation;
import com.phonepe.drove.models.operation.ops.ApplicationScaleOperation;
import io.appform.functionmetrics.MonitoredFunction;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.LongStream;

import static com.phonepe.drove.controller.utils.ControllerUtils.safeCast;

/**
 *
 */
@Slf4j
public class ScaleAppAction extends AppAsyncAction {
    private final ApplicationStateDB applicationStateDB;
    private final ClusterResourcesDB clusterResourcesDB;
    private final InstanceScheduler scheduler;
    private final ControllerCommunicator communicator;

    @Inject
    public ScaleAppAction(
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
        val scaleOp = safeCast(operation, ApplicationScaleOperation.class);
        long required = scaleOp.getRequiredInstances();
        val currentInstances = applicationStateDB.healthyInstances(scaleOp.getAppId());
        val currentInstancesCount = currentInstances.size();
        if (currentInstancesCount == required) {
            log.info("Nothing needs to be done for scaling as app {} already has required number of instances: {}",
                     context.getAppId(), required);
            return Optional.empty();
        }
        val applicationSpec = context.getApplicationSpec();
        val clusterOpSpec = scaleOp.getOpSpec();
        val parallelism = clusterOpSpec.getParallelism();
        if (currentInstancesCount < required) {
            val numNew = required - currentInstancesCount;
            val schedulingSessionId = UUID.randomUUID().toString();
            context.setSchedulingSessionId(schedulingSessionId);
            log.info("{} new instances to be started. Sched session ID: {}", numNew, schedulingSessionId);
            return Optional.of(JobTopology.<Boolean>builder()
                                       .addParallel(
                                               parallelism,
                                               LongStream.range(0, numNew)
                                                       .mapToObj(i -> (Job<Boolean>)new StartSingleInstanceJob(applicationSpec,
                                                                                                               clusterOpSpec,
                                                                                                               scheduler,
                                                                                                               applicationStateDB,
                                                                                                               communicator,
                                                                                                               schedulingSessionId))
                                                       .toList())
                                       .build());
        }
        else {
            val numToBeStopped = currentInstancesCount - required;
            log.info("{} instances to be stopped", numToBeStopped);
            val healthyInstances = new ArrayList<>(
                    applicationStateDB.activeInstances(scaleOp.getAppId(), 0, Integer.MAX_VALUE)
                            .stream()
                            .filter(instance -> instance.getState().equals(InstanceState.HEALTHY))
                            .toList());
            Collections.shuffle(healthyInstances);  //This is needed to reduce chances of selected instances to be skewed
            val instancesToBeStopped = healthyInstances
                    .stream()
                    .limit(numToBeStopped)
                    .toList();
            if (instancesToBeStopped.isEmpty()) {
                log.warn("Looks like instances are in inconsistent state. Tried to find extra instances but could not");
            }
            else {
                return Optional.of(JobTopology.<Boolean>builder()
                                           .addParallel(clusterOpSpec.getParallelism(), instancesToBeStopped
                                                   .stream()
                                                   .map(InstanceInfo::getInstanceId)
                                                   .map(iid -> (Job<Boolean>)new StopSingleInstanceJob(scaleOp.getAppId(),
                                                                                         iid,
                                                                                         scaleOp.getOpSpec(),
                                                                                         applicationStateDB,
                                                                                         clusterResourcesDB,
                                                                                         communicator))
                                                   .toList())
                                           .build());
            }
        }
        log.warn("Could not figure out number of ops to perform." +
                         " Will skip this time and revisit next health check cycle");
        return Optional.empty();
    }

    @Override
    @MonitoredFunction
    protected StateData<ApplicationState, ApplicationInfo> processResult(
            AppActionContext context,
            StateData<ApplicationState, ApplicationInfo> currentState,
            ApplicationOperation operation,
            JobExecutionResult<Boolean> executionResult) {
        val message = executionResult.getFailure() == null
                      ? "Execution failed"
                      : "Execution of jobs failed with error: " + executionResult.getFailure().getMessage();
        var errMsg = Boolean.TRUE.equals(executionResult.getResult())
                     ? ""
                     : message;
        val count = applicationStateDB.instanceCount(context.getAppId(), InstanceState.HEALTHY);
        val scaleOp = safeCast(operation, ApplicationScaleOperation.class);
        if (scaleOp.getRequiredInstances() == count) {
            log.info("Required scale {} has been reached.", count);
        }
        else {
            if (executionResult.isCancelled()) {
                log.warn(
                        "Looks like jobs were cancelled. Rolling back instances count to what is present right now [{}].",
                        count);
                applicationStateDB.updateInstanceCount(context.getAppId(), count);
            }
            else {
                log.warn("Mismatch between expected instances: {} and actual: {}. Need to be re-looked at",
                         scaleOp.getRequiredInstances(), count);
                return StateData.errorFrom(currentState, ApplicationState.SCALING_REQUESTED, errMsg);
            }
        }
        if (!Strings.isNullOrEmpty(context.getSchedulingSessionId())) {
            scheduler.finaliseSession(context.getSchedulingSessionId());
            log.debug("Scheduling session {} is now closed", context.getSchedulingSessionId());
            context.setSchedulingSessionId(null);
        }
        if (count > 0) {
            return StateData.errorFrom(currentState, ApplicationState.RUNNING, errMsg);
        }
        return StateData.errorFrom(currentState, ApplicationState.MONITORING, errMsg);
    }

    @Override
    public boolean cancel(AppActionContext context) {
        return cancelCurrentJobs(context);
    }

}
