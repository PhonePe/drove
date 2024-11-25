/*
 *  Copyright (c) 2024 Original Author(s), PhonePe India Pvt. Ltd.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package com.phonepe.drove.controller.statemachine.applications.actions;

import com.google.common.base.Strings;
import com.phonepe.drove.auth.core.ApplicationInstanceTokenManager;
import com.phonepe.drove.common.net.HttpCaller;
import com.phonepe.drove.controller.engine.ControllerCommunicator;
import com.phonepe.drove.controller.engine.ControllerRetrySpecFactory;
import com.phonepe.drove.controller.engine.InstanceIdGenerator;
import com.phonepe.drove.controller.engine.jobs.StartSingleInstanceJob;
import com.phonepe.drove.controller.engine.jobs.StopSingleInstanceJob;
import com.phonepe.drove.jobexecutor.Job;
import com.phonepe.drove.jobexecutor.JobExecutionResult;
import com.phonepe.drove.jobexecutor.JobExecutor;
import com.phonepe.drove.jobexecutor.JobTopology;
import com.phonepe.drove.controller.resourcemgmt.ClusterResourcesDB;
import com.phonepe.drove.controller.resourcemgmt.InstanceScheduler;
import com.phonepe.drove.controller.statedb.ApplicationStateDB;
import com.phonepe.drove.controller.statedb.ApplicationInstanceInfoDB;
import com.phonepe.drove.controller.statemachine.applications.AppActionContext;
import com.phonepe.drove.controller.statemachine.applications.AppAsyncAction;
import com.phonepe.drove.models.application.ApplicationInfo;
import com.phonepe.drove.models.application.ApplicationState;
import com.phonepe.drove.models.instance.InstanceInfo;
import com.phonepe.drove.models.instance.InstanceState;
import com.phonepe.drove.models.operation.ApplicationOperation;
import com.phonepe.drove.models.operation.ops.ApplicationScaleOperation;
import io.appform.functionmetrics.MonitoredFunction;
import com.phonepe.drove.statemachine.StateData;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

import javax.inject.Inject;
import javax.inject.Named;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadFactory;
import java.util.stream.LongStream;

import static com.phonepe.drove.controller.utils.ControllerUtils.safeCast;

/**
 *
 */
@Slf4j
public class ScaleAppAction extends AppAsyncAction {
    private final ApplicationStateDB applicationStateDB;
    private final ApplicationInstanceInfoDB instanceInfoDB;
    private final ClusterResourcesDB clusterResourcesDB;
    private final InstanceScheduler scheduler;
    private final ControllerCommunicator communicator;
    private final ControllerRetrySpecFactory retrySpecFactory;
    private final InstanceIdGenerator instanceIdGenerator;
    private final ThreadFactory threadFactory;

    private final ApplicationInstanceTokenManager tokenManager;
    private final HttpCaller httpCaller;

    @Inject
    public ScaleAppAction(
            JobExecutor<Boolean> jobExecutor,
            ApplicationStateDB applicationStateDB,
            ApplicationInstanceInfoDB instanceInfoDB,
            ClusterResourcesDB clusterResourcesDB,
            InstanceScheduler scheduler,
            ControllerCommunicator communicator,
            ControllerRetrySpecFactory retrySpecFactory,
            InstanceIdGenerator instanceIdGenerator,
            @Named("JobLevelThreadFactory") ThreadFactory threadFactory,
            ApplicationInstanceTokenManager tokenManager,
            HttpCaller httpCaller) {
        super(jobExecutor, instanceInfoDB);
        this.applicationStateDB = applicationStateDB;
        this.instanceInfoDB = instanceInfoDB;
        this.clusterResourcesDB = clusterResourcesDB;
        this.scheduler = scheduler;
        this.communicator = communicator;
        this.retrySpecFactory = retrySpecFactory;
        this.instanceIdGenerator = instanceIdGenerator;
        this.threadFactory = threadFactory;
        this.tokenManager = tokenManager;
        this.httpCaller = httpCaller;
    }

    @Override
    @MonitoredFunction
    protected Optional<JobTopology<Boolean>> jobsToRun(
            AppActionContext context,
            StateData<ApplicationState, ApplicationInfo> currentState,
            ApplicationOperation operation) {
        val scaleOp = safeCast(operation, ApplicationScaleOperation.class);
        long required = scaleOp.getRequiredInstances();
        val currentInstances = instanceInfoDB.healthyInstances(scaleOp.getAppId());
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
                                       .withThreadFactory(threadFactory)
                                       .addParallel(
                                               parallelism,
                                               LongStream.range(0, numNew)
                                                       .mapToObj(i -> (Job<Boolean>)new StartSingleInstanceJob(applicationSpec,
                                                                                                               clusterOpSpec,
                                                                                                               scheduler,
                                                                                                               instanceInfoDB,
                                                                                                               communicator,
                                                                                                               schedulingSessionId,
                                                                                                               retrySpecFactory,
                                                                                                               instanceIdGenerator,
                                                                                                               tokenManager,
                                                                                                               httpCaller))
                                                       .toList())
                                       .build());
        }
        else {
            val numToBeStopped = currentInstancesCount - required;
            log.info("{} instances to be stopped", numToBeStopped);
            val healthyInstances = new ArrayList<>(
                    instanceInfoDB.activeInstances(scaleOp.getAppId(), 0, Integer.MAX_VALUE)
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
                                           .withThreadFactory(threadFactory)
                                           .addParallel(clusterOpSpec.getParallelism(), instancesToBeStopped
                                                   .stream()
                                                   .map(InstanceInfo::getInstanceId)
                                                   .map(iid -> (Job<Boolean>)new StopSingleInstanceJob(scaleOp.getAppId(),
                                                                                                       iid,
                                                                                                       scaleOp.getOpSpec(),
                                                                                                       scheduler,
                                                                                                       null,
                                                                                                       instanceInfoDB,
                                                                                                       clusterResourcesDB,
                                                                                                       communicator,
                                                                                                       retrySpecFactory))
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
        val count = instanceInfoDB.instanceCount(context.getAppId(), InstanceState.HEALTHY);
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
