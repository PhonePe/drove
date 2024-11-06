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
import com.phonepe.drove.common.model.utils.Pair;
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
import com.phonepe.drove.models.operation.localserviceops.LocalServiceScaleOperation;
import com.phonepe.drove.statemachine.StateData;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

import javax.inject.Inject;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static com.phonepe.drove.controller.utils.ControllerUtils.safeCast;

/**
 *
 */
@Slf4j
public class ScaleLocalServiceAction extends LocalServiceAsyncAction {

    private final ClusterResourcesDB clusterResourcesDB;
    private final InstanceScheduler scheduler;
    private final ControllerCommunicator communicator;
    private final ControllerRetrySpecFactory retrySpecFactory;
    private final InstanceIdGenerator instanceIdGenerator;
    private final ApplicationInstanceTokenManager tokenManager;
    private final HttpCaller httpCaller;

    @Inject
    public ScaleLocalServiceAction(
            JobExecutor<Boolean> jobExecutor,
            LocalServiceStateDB stateDB,
            ClusterResourcesDB clusterResourcesDB,
            InstanceScheduler scheduler,
            ControllerCommunicator communicator,
            ControllerRetrySpecFactory retrySpecFactory,
            InstanceIdGenerator instanceIdGenerator,
            ApplicationInstanceTokenManager tokenManager,
            HttpCaller httpCaller) {
        super(jobExecutor, stateDB);
        this.clusterResourcesDB = clusterResourcesDB;
        this.scheduler = scheduler;
        this.communicator = communicator;
        this.retrySpecFactory = retrySpecFactory;
        this.instanceIdGenerator = instanceIdGenerator;
        this.tokenManager = tokenManager;
        this.httpCaller = httpCaller;
    }

    @Override
    protected Optional<JobTopology<Boolean>> jobsToRun(
            LocalServiceActionContext context,
            StateData<LocalServiceState, LocalServiceInfo> currentState,
            LocalServiceOperation operation) {
        val serviceId = safeCast(operation, LocalServiceScaleOperation.class).getServiceId();
        val instancesByExecutor = stateDB.instances(serviceId, LocalServiceInstanceState.ACTIVE_STATES, false)
                .stream()
                .collect(Collectors.groupingBy(LocalServiceInstanceInfo::getExecutorId));
        val currInfo = currentState.getData();
        val instancesPerHost = currInfo.getInstancesPerHost();
        val executors = clusterResourcesDB.currentSnapshot(true);
        return switch (currInfo.getState()) {
            case ACTIVE -> {
                val newInstancesPerExecutor = executors.stream()
                        .map(executorHostInfo -> Pair.of(executorHostInfo.getExecutorId(),
                                                         Math.max(0,
                                                                  instancesPerHost - instancesByExecutor.getOrDefault(
                                                                          executorHostInfo.getExecutorId(),
                                                                          List.of()).size())))
                        .filter(pair -> pair.getSecond() > 0)
                        .collect(Collectors.toMap(Pair::getFirst, Pair::getSecond));
                if (newInstancesPerExecutor.isEmpty()) {
                    log.info("No new instances are needed to be spun up for {}", serviceId);
                    yield Optional.empty();
                }
                val schedulingSessionId = UUID.randomUUID().toString();
                yield Optional.of(
                        JobTopology.<Boolean>builder()
                                .addJob(
                                        newInstancesPerExecutor.entrySet()
                                                .stream()
                                                .flatMap(entry -> {
                                                    val executorId = entry.getKey();
                                                    return IntStream.range(0, entry.getValue())
                                                            .mapToObj(i -> new StartSingleLocalServiceInstanceJob(
                                                                    currInfo,
                                                                    ClusterOpSpec.DEFAULT,
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
                                                .map(x -> (Job<Boolean>) x)
                                                .toList())
                                .build());
            }
            case INACTIVE, UNKNOWN -> {
                val extraInstances = executors.stream()
                        .flatMap(executorHostInfo -> executorHostInfo.getNodeData().getServiceInstances().stream())
                        .filter(serviceInstance -> LocalServiceInstanceState.RUNNING_STATES.contains(serviceInstance.getState()))
                        .map(LocalServiceInstanceInfo::getInstanceId)
                        .toList();
                if (extraInstances.isEmpty()) {
                    log.info("No instances need to be shut down for inactive service {}", serviceId);
                    yield Optional.empty();
                }
                yield Optional.of(
                        JobTopology.<Boolean>builder()
                                .addJob(
                                        extraInstances.stream()
                                                .map(instanceId -> new StopSingleLocalServiceInstanceJob(
                                                        currInfo.getServiceId(),
                                                        instanceId,
                                                        ClusterOpSpec.DEFAULT,
                                                        scheduler,
                                                        null,
                                                        stateDB,
                                                        clusterResourcesDB,
                                                        communicator,
                                                        retrySpecFactory))
                                                .map(x -> (Job<Boolean>) x)
                                                .toList())
                                .build());
            }
        };

    }

    @Override
    protected StateData<LocalServiceState, LocalServiceInfo> processResult(
            LocalServiceActionContext context,
            StateData<LocalServiceState, LocalServiceInfo> currentState,
            LocalServiceOperation operation,
            JobExecutionResult<Boolean> executionResult) {
        log.info("Execution result: {}", executionResult);
        return StateData.from(currentState, switch (currentState.getData().getState()) {
            case ACTIVE -> LocalServiceState.ACTIVE;
            case INACTIVE, UNKNOWN -> LocalServiceState.INACTIVE;
        });
    }
}
