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

import com.phonepe.drove.auth.core.ApplicationInstanceTokenManager;
import com.phonepe.drove.common.CommonUtils;
import com.phonepe.drove.common.net.HttpCaller;
import com.phonepe.drove.controller.engine.ControllerCommunicator;
import com.phonepe.drove.controller.engine.ControllerRetrySpecFactory;
import com.phonepe.drove.controller.engine.InstanceIdGenerator;
import com.phonepe.drove.controller.engine.jobs.StartSingleLocalServiceInstanceJob;
import com.phonepe.drove.controller.engine.jobs.StopSingleLocalServiceInstanceJob;
import com.phonepe.drove.controller.resourcemgmt.ClusterResourcesDB;
import com.phonepe.drove.controller.resourcemgmt.InstanceScheduler;
import com.phonepe.drove.controller.statedb.LocalServiceStateDB;
import com.phonepe.drove.controller.statemachine.localservice.LocalServiceActionContext;
import com.phonepe.drove.controller.statemachine.localservice.LocalServiceAsyncAction;
import com.phonepe.drove.controller.utils.ControllerUtils;
import com.phonepe.drove.jobexecutor.Job;
import com.phonepe.drove.jobexecutor.JobExecutionResult;
import com.phonepe.drove.jobexecutor.JobExecutor;
import com.phonepe.drove.jobexecutor.JobTopology;
import com.phonepe.drove.models.instance.LocalServiceInstanceState;
import com.phonepe.drove.models.localservice.ActivationState;
import com.phonepe.drove.models.localservice.LocalServiceInfo;
import com.phonepe.drove.models.localservice.LocalServiceInstanceInfo;
import com.phonepe.drove.models.localservice.LocalServiceState;
import com.phonepe.drove.models.operation.ClusterOpSpec;
import com.phonepe.drove.models.operation.LocalServiceOperation;
import com.phonepe.drove.models.operation.localserviceops.LocalServiceAdjustInstancesOperation;
import com.phonepe.drove.statemachine.StateData;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.jetbrains.annotations.NotNull;

import javax.inject.Inject;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static com.phonepe.drove.controller.utils.ControllerUtils.safeCast;

/**
 * Adjusts instances across executors by spinning up new instances or killing extra ones where necessary
 */
@Slf4j
public class AdjustInstancesLocalServiceAction extends LocalServiceAsyncAction {

    private final ClusterResourcesDB clusterResourcesDB;
    private final InstanceScheduler scheduler;
    private final ControllerCommunicator communicator;
    private final ControllerRetrySpecFactory retrySpecFactory;
    private final InstanceIdGenerator instanceIdGenerator;
    private final ApplicationInstanceTokenManager tokenManager;
    private final HttpCaller httpCaller;
    private final ClusterOpSpec defaultClusterOpSpec;

    @Inject
    public AdjustInstancesLocalServiceAction(
            JobExecutor<Boolean> jobExecutor,
            LocalServiceStateDB stateDB,
            ClusterResourcesDB clusterResourcesDB,
            InstanceScheduler scheduler,
            ControllerCommunicator communicator,
            ControllerRetrySpecFactory retrySpecFactory,
            InstanceIdGenerator instanceIdGenerator,
            ApplicationInstanceTokenManager tokenManager,
            HttpCaller httpCaller,
            ClusterOpSpec defaultClusterOpSpec) {
        super(jobExecutor, stateDB);
        this.clusterResourcesDB = clusterResourcesDB;
        this.scheduler = scheduler;
        this.communicator = communicator;
        this.retrySpecFactory = retrySpecFactory;
        this.instanceIdGenerator = instanceIdGenerator;
        this.tokenManager = tokenManager;
        this.httpCaller = httpCaller;
        this.defaultClusterOpSpec = defaultClusterOpSpec;
    }

    @Override
    protected Optional<JobTopology<Boolean>> jobsToRun(
            LocalServiceActionContext context,
            StateData<LocalServiceState, LocalServiceInfo> currentState,
            LocalServiceOperation operation) {
        val scaleOp = safeCast(operation, LocalServiceAdjustInstancesOperation.class);
        val serviceId = scaleOp.getServiceId();
        val instancesByExecutor = stateDB.instances(serviceId, LocalServiceInstanceState.ACTIVE_STATES, false)
                .stream()
                .collect(Collectors.groupingBy(LocalServiceInstanceInfo::getExecutorId));
        val currInfo = currentState.getData();
        val instancesPerHost = currInfo.getInstancesPerHost();
        val liveExecutors = clusterResourcesDB.currentSnapshot(true);
        val clusterOpSpec = Objects.requireNonNullElse(scaleOp.getOpSpec(), defaultClusterOpSpec);
        return switch (currInfo.getActivationState()) {
            case ACTIVE -> {
                val newInstancesPerExecutor = new HashMap<String, Integer>();
                val extraInstances = new HashSet<String>();
                liveExecutors.forEach(executorHostInfo -> {
                    val instancesOnExecutor = instancesByExecutor.getOrDefault(
                            executorHostInfo.getExecutorId(), List.of());
                    if (instancesOnExecutor.size() < instancesPerHost) {
                        newInstancesPerExecutor.compute(
                                executorHostInfo.getExecutorId(),
                                (id, existing) ->
                                        Objects.requireNonNullElse(existing, 0)
                                                + instancesPerHost - instancesOnExecutor.size());
                    }
                    else if (instancesOnExecutor.size() > instancesPerHost) {
                        val runningInstances = instancesOnExecutor
                                .stream()
                                .filter(AdjustInstancesLocalServiceAction::isInRunningState)
                                .map(LocalServiceInstanceInfo::getInstanceId)
                                .toList();
                        //Ignore instances not in running states, they will
                        // be handled in next pass if they become healthy
                        extraInstances.addAll(CommonUtils.sublist(runningInstances,
                                                                  instancesPerHost,
                                                                  runningInstances.size()));
                    }
                });
                val schedulingSessionId = UUID.randomUUID().toString();
                val jobList = new ArrayList<Job<Boolean>>();

                if (newInstancesPerExecutor.isEmpty()) {
                    log.info("No new instances are needed to be spun up for {}", serviceId);
                }
                else {
                    log.info("Will create the following instances: {}",
                             newInstancesPerExecutor.entrySet()
                                     .stream()
                                     .map(e -> "%s:%d".formatted(e.getKey(), e.getValue()))
                                     .toList());
                    jobList.addAll(createNewInstanceJobs(newInstancesPerExecutor,
                                                         currInfo,
                                                         clusterOpSpec,
                                                         schedulingSessionId));
                }
                if (extraInstances.isEmpty()) {
                    log.info("No extra instances found for: {}", serviceId);
                }
                else {
                    log.info("Will kill the following extra instances: {}", extraInstances);
                    jobList.addAll(createStopJobs(extraInstances, currInfo, clusterOpSpec, schedulingSessionId));
                }
                if (jobList.isEmpty()) {
                    log.info("Everything kosher for {}. No adjustment needed.", serviceId);
                    yield Optional.empty();
                }
                yield Optional.of(JobTopology.<Boolean>builder()
                                          .addJob(jobList)
                                          .build());
            }
            case CONFIG_TESTING -> {
                Collections.shuffle(liveExecutors);
                val executor = liveExecutors.stream().findAny().orElse(null);
                if(executor == null) {
                    log.info("There are no executors on the cluster. Will not create any instances");
                    yield Optional.empty();
                }
                log.info("Will spin up test instance on {}", executor.getExecutorId());
                val schedulingSessionId = UUID.randomUUID().toString();
                yield Optional.of(JobTopology.<Boolean>builder()
                                          .addJob(createNewInstanceJobs(Map.of(executor.getExecutorId(), 1),
                                                                        currInfo,
                                                                        clusterOpSpec,
                                                                        schedulingSessionId))
                                          .build());
            }
            case INACTIVE -> {
                val extraInstances = liveExecutors.stream()
                        .flatMap(executorHostInfo -> Objects.requireNonNullElse(
                                        executorHostInfo.getNodeData().getServiceInstances(),
                                        List.<LocalServiceInstanceInfo>of())
                                .stream())
                        .filter(AdjustInstancesLocalServiceAction::isInRunningState)
                        .map(LocalServiceInstanceInfo::getInstanceId)
                        .toList();
                if (extraInstances.isEmpty()) {
                    log.info("No instances need to be shut down for inactive service {}", serviceId);
                    yield Optional.empty();
                }
                yield Optional.of(
                        JobTopology.<Boolean>builder()
                                .addJob(createStopJobs(extraInstances, currInfo, clusterOpSpec, null))
                                .build());
            }
        };

    }

    @Override
    public boolean cancel(LocalServiceActionContext context) {
        return cancelCurrentJobs(context);
    }

    private static boolean isInRunningState(LocalServiceInstanceInfo serviceInstance) {
        return LocalServiceInstanceState.RUNNING_STATES.contains(serviceInstance.getState());
    }

    @NotNull
    private List<Job<Boolean>> createStopJobs(
            Collection<String> extraInstances,
            LocalServiceInfo currInfo,
            ClusterOpSpec clusterOpSpec,
            String schedulingSessionId) {
        return extraInstances.stream()
                .map(instanceId -> new StopSingleLocalServiceInstanceJob(
                        currInfo.getServiceId(),
                        instanceId,
                        clusterOpSpec,
                        scheduler,
                        schedulingSessionId,
                        stateDB,
                        clusterResourcesDB,
                        communicator,
                        retrySpecFactory))
                .map(x -> (Job<Boolean>) x)
                .toList();
    }

    @NotNull
    private List<Job<Boolean>> createNewInstanceJobs(
            Map<String, Integer> newInstancesPerExecutor,
            LocalServiceInfo currInfo,
            ClusterOpSpec clusterOpSpec,
            String schedulingSessionId) {
        return newInstancesPerExecutor.entrySet()
                .stream()
                .flatMap(entry -> {
                    val executorId = entry.getKey();
                    return IntStream.range(0, entry.getValue())
                            .mapToObj(i -> (Job<Boolean>) new StartSingleLocalServiceInstanceJob(
                                    currInfo,
                                    clusterOpSpec,
                                    scheduler,
                                    stateDB,
                                    communicator,
                                    schedulingSessionId,
                                    retrySpecFactory,
                                    instanceIdGenerator,
                                    tokenManager,
                                    httpCaller,
                                    clusterResourcesDB.currentSnapshot(executorId)
                                            .orElse(null)));
                })
                .toList();
    }

    @Override
    protected StateData<LocalServiceState, LocalServiceInfo> processResult(
            LocalServiceActionContext context,
            StateData<LocalServiceState, LocalServiceInfo> currentState,
            LocalServiceOperation operation,
            JobExecutionResult<Boolean> executionResult) {
        log.debug("Execution result: {}", executionResult);
        val activationState = currentState.getData().getActivationState();
        if (executionResult.isCancelled()) {
            if (activationState.equals(ActivationState.ACTIVE) || activationState.equals(ActivationState.CONFIG_TESTING)) {
                log.info("Job has been cancelled for some reason. Will request deactivation for safety");
                return StateData.from(currentState, LocalServiceState.EMERGENCY_DEACTIVATION_REQUESTED);
            }
        }
        return StateData.from(currentState, ControllerUtils.serviceActivationStateToSMState(activationState));
    }
}
