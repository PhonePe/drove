package com.phonepe.drove.controller.statemachine.actions;

import com.phonepe.drove.common.StateData;
import com.phonepe.drove.controller.engine.ControllerCommunicator;
import com.phonepe.drove.controller.engine.jobs.StartSingleInstanceJob;
import com.phonepe.drove.controller.engine.jobs.StopSingleInstanceJob;
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
import lombok.extern.slf4j.Slf4j;
import lombok.val;

import javax.inject.Inject;
import java.util.stream.Collectors;
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
    protected JobTopology<Boolean> jobsToRun(
            AppActionContext context,
            StateData<ApplicationState, ApplicationInfo> currentState,
            ApplicationOperation operation) {
        val scaleOp = safeCast(operation, ApplicationScaleOperation.class);
        long required = scaleOp.getRequiredInstances();
        val currentInstances = applicationStateDB.healthyInstances(scaleOp.getAppId());
        val currentInstancesCount = currentInstances.size();
        if (currentInstancesCount == required) {
            return null;
        }
        val applicationSpec = context.getApplicationSpec();
        val clusterOpSpec = scaleOp.getOpSpec();
        val parallelism = clusterOpSpec.getParallelism();
        if (currentInstancesCount < required) {
            val numNew = required - currentInstancesCount;
            log.info("{} new instances to be started", numNew);
            return JobTopology.<Boolean>builder()
                    .addParallel(parallelism, LongStream.range(0, numNew)
                            .mapToObj(i -> new StartSingleInstanceJob(applicationSpec,
                                                                      clusterOpSpec,
                                                                      scheduler,
                                                                      applicationStateDB,
                                                                      communicator))
                            .collect(Collectors.toUnmodifiableList()))
                    .build();
        }
        else {
            val numToBeStopped = currentInstancesCount - required;
            log.info("{} instances to be stopped", numToBeStopped);
            val instancesToBeStopped = applicationStateDB.instances(scaleOp.getAppId(), 0, Integer.MAX_VALUE)
                    .stream()
                    .filter(instance -> instance.getState().equals(InstanceState.HEALTHY))
                    .limit(numToBeStopped)
                    .collect(Collectors.toUnmodifiableList());
            if (instancesToBeStopped.isEmpty()) {
                log.warn(
                        "Looks like instances are in inconsistent state. Tried to find extra instances but could not");
            }
            else {
                return JobTopology.<Boolean>builder()
                        .addParallel(clusterOpSpec.getParallelism(), instancesToBeStopped
                                .stream()
                                .map(InstanceInfo::getInstanceId)
                                .map(iid -> new StopSingleInstanceJob(scaleOp.getAppId(),
                                                                      iid,
                                                                      scaleOp.getOpSpec(),
                                                                      applicationStateDB,
                                                                      clusterResourcesDB,
                                                                      communicator))
                                .collect(Collectors.toUnmodifiableList()))
                        .build();
            }
        }
        return JobTopology.<Boolean>builder().build(); //TODO::FIX THIS
    }

    @Override
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
        val scaleOp = safeCast(operation, ApplicationScaleOperation.class);
        if (scaleOp.getRequiredInstances() == count) {
            log.info("Required scale {} has been reached.", count);
//                applicationStateDB.updateInstanceCount(scaleOp.getAppId(), count);
        }
        else {
            log.warn("Mismatch between expected instances: {} and actual: {}. Need to be re-looked at",
                     scaleOp.getRequiredInstances(),
                     count);
/*                context.recordUpdate(
                        new ApplicationUpdateData(
                                new ApplicationScaleOperation(context.getAppId(), count, ClusterOpSpec.DEFAULT),
                                null));*/
            return StateData.errorFrom(currentState, ApplicationState.SCALING_REQUESTED, errMsg);
        }
        if (count > 0) {
            return StateData.errorFrom(currentState, ApplicationState.RUNNING, errMsg);
        }
        return StateData.errorFrom(currentState, ApplicationState.MONITORING, errMsg);
    }
}
