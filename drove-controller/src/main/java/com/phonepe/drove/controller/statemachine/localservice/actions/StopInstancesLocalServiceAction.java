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

import com.phonepe.drove.controller.engine.ControllerCommunicator;
import com.phonepe.drove.controller.engine.ControllerRetrySpecFactory;
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
import com.phonepe.drove.models.operation.localserviceops.LocalServiceStopInstancesOperation;
import com.phonepe.drove.statemachine.StateData;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

import javax.inject.Inject;
import javax.inject.Named;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ThreadFactory;
import java.util.stream.Collectors;

import static com.phonepe.drove.controller.utils.ControllerUtils.safeCast;

/**
 *
 */
@Slf4j
public class StopInstancesLocalServiceAction extends LocalServiceAsyncAction {
    private final LocalServiceStateDB localServiceStateDB;
    private final ClusterResourcesDB clusterResourcesDB;
    private final InstanceScheduler scheduler;
    private final ControllerCommunicator communicator;
    private final ControllerRetrySpecFactory retrySpecFactory;

    private final ThreadFactory threadFactory;
    private final ClusterOpSpec defaultOpSpec;

    @Inject
    public StopInstancesLocalServiceAction(
            JobExecutor<Boolean> jobExecutor,
            LocalServiceStateDB localServiceStateDB,
            ClusterResourcesDB clusterResourcesDB,
            InstanceScheduler scheduler,
            ControllerCommunicator communicator,
            ControllerRetrySpecFactory retrySpecFactory,
            @Named("JobLevelThreadFactory") ThreadFactory threadFactory,
            ClusterOpSpec defaultOpSpec) {
        super(jobExecutor, localServiceStateDB);
        this.localServiceStateDB = localServiceStateDB;
        this.clusterResourcesDB = clusterResourcesDB;
        this.scheduler = scheduler;
        this.communicator = communicator;
        this.retrySpecFactory = retrySpecFactory;
        this.threadFactory = threadFactory;
        this.defaultOpSpec = defaultOpSpec;
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
        val stopOp = safeCast(operation, LocalServiceStopInstancesOperation.class);
        val serviceId = stopOp.getServiceId();
        val requestedInstances = stopOp.getInstanceIds();
        val affectedInstances = localServiceStateDB.instances(serviceId,
                                                              EnumSet.of(LocalServiceInstanceState.HEALTHY),
                                                              false)
                .stream()
                .filter(instanceInfo -> requestedInstances.contains(instanceInfo.getInstanceId()))
                .toList();
        if (affectedInstances.isEmpty()) {
            log.info("Nothing done to stop instances for {}. No relevant instances found.", serviceId);
            return Optional.empty();
        }
        val clusterOpSpec = Objects.requireNonNullElse(stopOp.getOpSpec(), defaultOpSpec);
        val serviceInfo = localServiceStateDB.service(serviceId).orElse(null);
        if (null == serviceInfo) {
            return Optional.empty();
        }

        val parallelism = clusterOpSpec.getParallelism();
        log.info("{} instances to be stopped with parallelism: {}.", affectedInstances.size(), parallelism);

        val stopJobs = affectedInstances.stream()
                .map(instanceInfo -> (Job<Boolean>)new StopSingleLocalServiceInstanceJob(
                        serviceId,
                        instanceInfo.getInstanceId(),
                        clusterOpSpec,
                        scheduler,
                        null,
                        localServiceStateDB,
                        clusterResourcesDB,
                        communicator,
                        retrySpecFactory))
                .toList();
        return Optional.of(JobTopology.<Boolean>builder()
                                   .withThreadFactory(threadFactory)
                                   .addParallel(parallelism, stopJobs)
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
        val stopOp = safeCast(operation, LocalServiceStopInstancesOperation.class);

        val instances = localServiceStateDB.instances(context.getServiceId(),
                                                      EnumSet.of(LocalServiceInstanceState.HEALTHY),
                                                      false)
                .stream()
                .map(LocalServiceInstanceInfo::getInstanceId)
                .collect(Collectors.toUnmodifiableSet());
        if (!instances.containsAll(stopOp.getInstanceIds())) {
            log.info("All requested instances have been stopped");
            return determineState(currentState, null);
        }
        return determineState(currentState, errMsg);
    }
}
