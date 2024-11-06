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

package com.phonepe.drove.controller.statemachine.localservice.actions;

import com.google.common.base.Strings;
import com.phonepe.drove.auth.core.ApplicationInstanceTokenManager;
import com.phonepe.drove.common.net.HttpCaller;
import com.phonepe.drove.controller.engine.ControllerCommunicator;
import com.phonepe.drove.controller.engine.ControllerRetrySpecFactory;
import com.phonepe.drove.controller.engine.InstanceIdGenerator;
import com.phonepe.drove.controller.engine.jobs.StartSingleLocalServiceInstanceJob;
import com.phonepe.drove.controller.engine.jobs.StopSingleLocalServiceInstanceJob;
import com.phonepe.drove.controller.resourcemgmt.ClusterResourcesDB;
import com.phonepe.drove.controller.resourcemgmt.ExecutorHostInfo;
import com.phonepe.drove.controller.resourcemgmt.InstanceScheduler;
import com.phonepe.drove.controller.statedb.LocalServiceStateDB;
import com.phonepe.drove.controller.statemachine.localservice.LocalServiceActionContext;
import com.phonepe.drove.controller.statemachine.localservice.LocalServiceAsyncAction;
import com.phonepe.drove.jobexecutor.Job;
import com.phonepe.drove.jobexecutor.JobExecutionResult;
import com.phonepe.drove.jobexecutor.JobExecutor;
import com.phonepe.drove.jobexecutor.JobTopology;
import com.phonepe.drove.models.instance.LocalServiceInstanceState;
import com.phonepe.drove.models.localservice.LocalServiceInfo;
import com.phonepe.drove.models.localservice.LocalServiceInstanceInfo;
import com.phonepe.drove.models.localservice.LocalServiceState;
import com.phonepe.drove.models.operation.ClusterOpSpec;
import com.phonepe.drove.models.operation.LocalServiceOperation;
import com.phonepe.drove.models.operation.localserviceops.LocalServiceReplaceInstancesOperation;
import com.phonepe.drove.statemachine.StateData;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

import javax.inject.Inject;
import javax.inject.Named;
import java.util.*;
import java.util.concurrent.ThreadFactory;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.phonepe.drove.controller.utils.ControllerUtils.safeCast;
import static java.util.stream.Collectors.toMap;

/**
 *
 */
@Slf4j
public class ReplaceInstancesLocalServiceAction extends LocalServiceAsyncAction {
    private final LocalServiceStateDB localServiceStateDB;
    private final ClusterResourcesDB clusterResourcesDB;
    private final InstanceScheduler scheduler;
    private final ControllerCommunicator communicator;
    private final ControllerRetrySpecFactory retrySpecFactory;
    private final InstanceIdGenerator instanceIdGenerator;

    private final ThreadFactory threadFactory;
    private final ApplicationInstanceTokenManager tokenManager;
    private final HttpCaller httpCaller;

    @Inject
    public ReplaceInstancesLocalServiceAction(
            JobExecutor<Boolean> jobExecutor,
            LocalServiceStateDB localServiceStateDB,
            ClusterResourcesDB clusterResourcesDB,
            InstanceScheduler scheduler,
            ControllerCommunicator communicator,
            ControllerRetrySpecFactory retrySpecFactory,
            InstanceIdGenerator instanceIdGenerator,
            @Named("JobLevelThreadFactory") ThreadFactory threadFactory,
            ApplicationInstanceTokenManager tokenManager,
            HttpCaller httpCaller) {
        super(jobExecutor, localServiceStateDB);
        this.localServiceStateDB = localServiceStateDB;
        this.clusterResourcesDB = clusterResourcesDB;
        this.scheduler = scheduler;
        this.communicator = communicator;
        this.retrySpecFactory = retrySpecFactory;
        this.instanceIdGenerator = instanceIdGenerator;
        this.threadFactory = threadFactory;
        this.tokenManager = tokenManager;
        this.httpCaller = httpCaller;
    }


    private StopSingleLocalServiceInstanceJob stopOldInstanceJob(
            LocalServiceInstanceInfo instanceInfo,
            String serviceId,
            ClusterOpSpec clusterOpSpec,
            String schedulingSessionId) {
        return new StopSingleLocalServiceInstanceJob(
                serviceId,
                instanceInfo.getInstanceId(),
                clusterOpSpec,
                scheduler,
                schedulingSessionId,
                localServiceStateDB,
                clusterResourcesDB,
                communicator,
                retrySpecFactory);
    }

    private StartSingleLocalServiceInstanceJob startNewInstanceJob(
            LocalServiceInfo localServiceInfo,
            ClusterOpSpec clusterOpSpec,
            String schedulingSessionId,
            ExecutorHostInfo executor) {
        return new StartSingleLocalServiceInstanceJob(localServiceInfo,
                                                      clusterOpSpec,
                                                      scheduler,
                                                      localServiceStateDB, communicator,
                                                      schedulingSessionId,
                                                      retrySpecFactory,
                                                      instanceIdGenerator,
                                                      tokenManager,
                                                      httpCaller,
                                                      executor);
    }

    private String errorMessage(JobExecutionResult<Boolean> executionResult) {
        return executionResult.getFailure() == null
               ? "Execution failed"
               : "Execution of jobs failed with error: " + executionResult.getFailure().getMessage();
    }

    @Override
    protected Optional<JobTopology<Boolean>> jobsToRun(
            LocalServiceActionContext context,
            StateData<LocalServiceState, LocalServiceInfo> currentState,
            LocalServiceOperation operation) {
        val restartOp = safeCast(operation, LocalServiceReplaceInstancesOperation.class);
        val serviceId = restartOp.getServiceId();
        val requestedInstances = Objects.requireNonNullElse(restartOp.getInstanceIds(), Set.of());
        val affectedInstances = localServiceStateDB.instances(serviceId,
                                                              EnumSet.of(LocalServiceInstanceState.HEALTHY),
                                                              false)
                .stream()
                .filter(instanceInfo -> requestedInstances.isEmpty()
                        || requestedInstances.contains(instanceInfo.getInstanceId()))
                .toList();
        if (affectedInstances.isEmpty()) {
            log.info("Nothing done to replace instances for {}. No relevant instances found.", serviceId);
            return Optional.empty();
        }
        val clusterOpSpec = Objects.requireNonNullElse(restartOp.getOpSpec(), ClusterOpSpec.DEFAULT);
        val serviceInfo = localServiceStateDB.service(serviceId).orElse(null);
        if (null == serviceInfo) {
            return Optional.empty();
        }

        val parallelism = clusterOpSpec.getParallelism();
        val schedulingSessionId = UUID.randomUUID().toString();
        context.setSchedulingSessionId(schedulingSessionId);

        log.info("{} instances to be restarted with parallelism: {}. Sched session ID: {}",
                 affectedInstances.size(),
                 parallelism,
                 schedulingSessionId);
        val stopFirst = restartOp.isStopFirst();
        log.info("Stop first enforced: {}", stopFirst);
        val executors = clusterResourcesDB.currentSnapshot(false)
                .stream()
                .collect(toMap(ExecutorHostInfo::getExecutorId, Function.identity()));
        val restartJobs = affectedInstances.stream()
                .map(instanceInfo -> (Job<Boolean>) JobTopology.<Boolean>builder()
                        .withThreadFactory(threadFactory)
                        .addJob(stopFirst
                                ? List.of(stopOldInstanceJob(instanceInfo,
                                                             serviceId,
                                                             clusterOpSpec,
                                                             schedulingSessionId),
                                          startNewInstanceJob(serviceInfo,
                                                              clusterOpSpec,
                                                              schedulingSessionId,
                                                              executors.get(instanceInfo.getExecutorId())))
                                : List.of(startNewInstanceJob(serviceInfo,
                                                              clusterOpSpec,
                                                              schedulingSessionId,
                                                              executors.get(instanceInfo.getExecutorId())),
                                          stopOldInstanceJob(instanceInfo,
                                                             serviceId,
                                                             clusterOpSpec,
                                                             schedulingSessionId)))
                        .build())
                .toList();
        return Optional.of(JobTopology.<Boolean>builder()
                                   .withThreadFactory(threadFactory)
                                   .addParallel(parallelism, restartJobs)
                                   .build());
    }

    @Override
    protected StateData<LocalServiceState, LocalServiceInfo> processResult(
            LocalServiceActionContext context,
            StateData<LocalServiceState, LocalServiceInfo> currentState,
            LocalServiceOperation operation,
            JobExecutionResult<Boolean> executionResult) {
        var errMsg = Boolean.TRUE.equals(executionResult.getResult())
                     ? ""
                     : errorMessage(executionResult);
        try {
            val restartOp = safeCast(operation, LocalServiceReplaceInstancesOperation.class);

            val instancesToBeStopped = Objects.requireNonNullElse(restartOp.getInstanceIds(), List.<String>of());
            if (!instancesToBeStopped.isEmpty()) {
                val instances = localServiceStateDB.instances(context.getServiceId(),
                                                              EnumSet.of(LocalServiceInstanceState.HEALTHY),
                                                              false)
                        .stream()
                        .map(LocalServiceInstanceInfo::getInstanceId)
                        .collect(Collectors.toUnmodifiableSet());
                if (!instances.containsAll(instancesToBeStopped)) {
                    return determineState(currentState, null);
                }
                return determineState(currentState, errMsg);
            }
            return determineState(currentState, errMsg);
        }
        finally {
            if (!Strings.isNullOrEmpty(context.getSchedulingSessionId())) {
                scheduler.finaliseSession(context.getSchedulingSessionId());
                log.debug("Scheduling session {} is now closed", context.getSchedulingSessionId());
                context.setSchedulingSessionId(null);
            }
        }
    }
}
