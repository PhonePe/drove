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
import com.phonepe.drove.controller.resourcemgmt.ClusterResourcesDB;
import com.phonepe.drove.controller.resourcemgmt.InstanceScheduler;
import com.phonepe.drove.controller.statedb.ApplicationInstanceInfoDB;
import com.phonepe.drove.controller.statedb.ApplicationStateDB;
import com.phonepe.drove.controller.statemachine.applications.AppActionContext;
import com.phonepe.drove.controller.statemachine.applications.AppAsyncAction;
import com.phonepe.drove.jobexecutor.Job;
import com.phonepe.drove.jobexecutor.JobExecutionResult;
import com.phonepe.drove.jobexecutor.JobExecutor;
import com.phonepe.drove.jobexecutor.JobTopology;
import com.phonepe.drove.models.application.ApplicationInfo;
import com.phonepe.drove.models.application.ApplicationSpec;
import com.phonepe.drove.models.application.ApplicationState;
import com.phonepe.drove.models.instance.InstanceInfo;
import com.phonepe.drove.models.instance.InstanceState;
import com.phonepe.drove.models.operation.ApplicationOperation;
import com.phonepe.drove.models.operation.ClusterOpSpec;
import com.phonepe.drove.models.operation.ops.ApplicationReplaceInstancesOperation;
import com.phonepe.drove.statemachine.StateData;
import io.appform.functionmetrics.MonitoredFunction;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

import javax.inject.Inject;
import javax.inject.Named;
import javax.validation.constraints.NotNull;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadFactory;

import static com.phonepe.drove.controller.utils.ControllerUtils.safeCast;

/**
 *
 */
@Slf4j
public class ReplaceInstancesAppAction extends AppAsyncAction {
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
    public ReplaceInstancesAppAction(
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
        val restartOp = safeCast(operation, ApplicationReplaceInstancesOperation.class);
        val appId = restartOp.getAppId();
        val instances = instanceInfoDB.healthyInstances(restartOp.getAppId())
                .stream()
                .filter(instanceInfo -> (restartOp.getInstanceIds() == null || restartOp.getInstanceIds().isEmpty())
                        || restartOp.getInstanceIds().contains(instanceInfo.getInstanceId()))
                .toList();
        if (instances.isEmpty()) {
            log.info("Nothing done to replace instances for {}. No relevant instances found.", appId);
            return Optional.empty();
        }
        val clusterOpSpec = restartOp.getOpSpec();
        val appSpec = applicationStateDB.application(appId).map(ApplicationInfo::getSpec).orElse(null);
        if (null == appSpec) {
            return Optional.empty();
        }
        int parallelism = clusterOpSpec.getParallelism();
        val schedulingSessionId = UUID.randomUUID().toString();
        context.setSchedulingSessionId(schedulingSessionId);

        log.info("{} instances to be restarted with parallelism: {}. Sched session ID: {}",
                 instances.size(),
                 parallelism,
                 schedulingSessionId);
        val stopFirst = restartOp.isStopFirst();
        log.info("Stop first enforced: {}", stopFirst);
        val restartJobs = instances.stream()
                .map(instanceInfo -> (Job<Boolean>) JobTopology.<Boolean>builder()
                        .withThreadFactory(threadFactory)
                        .addJob(stopFirst
                                ? List.of(stopOldInstanceJob(instanceInfo, appId, clusterOpSpec, schedulingSessionId),
                                          startNewInstanceJob(appSpec, clusterOpSpec, schedulingSessionId))
                                : List.of(startNewInstanceJob(appSpec, clusterOpSpec, schedulingSessionId),
                                          stopOldInstanceJob(instanceInfo, appId, clusterOpSpec, schedulingSessionId)))
                        .build())
                .toList();
        return Optional.of(JobTopology.<Boolean>builder()
                                   .withThreadFactory(threadFactory)
                                   .addParallel(parallelism, restartJobs)
                                   .build());
    }

    private StopSingleInstanceJob stopOldInstanceJob(
            InstanceInfo instanceInfo,
            String appId,
            ClusterOpSpec clusterOpSpec,
            String schedulingSessionId) {
        return new StopSingleInstanceJob(appId,
                                         instanceInfo.getInstanceId(),
                                         clusterOpSpec,
                                         scheduler,
                                         schedulingSessionId,
                                         instanceInfoDB, clusterResourcesDB,
                                         communicator, retrySpecFactory);
    }

    private @NotNull StartSingleInstanceJob startNewInstanceJob(
            ApplicationSpec appSpec,
            ClusterOpSpec clusterOpSpec,
            String schedulingSessionId) {
        return new StartSingleInstanceJob(appSpec,
                                          clusterOpSpec,
                                          scheduler,
                                          instanceInfoDB, communicator,
                                          schedulingSessionId,
                                          retrySpecFactory,
                                          instanceIdGenerator,
                                          tokenManager,
                                          httpCaller);
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
                     : errorMessage(executionResult);
        val count = instanceInfoDB.instanceCount(context.getAppId(), InstanceState.HEALTHY);
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

    private String errorMessage(JobExecutionResult<Boolean> executionResult) {
        return executionResult.getFailure() == null
               ? "Execution failed"
               : "Execution of jobs failed with error: " + executionResult.getFailure().getMessage();
    }
}
