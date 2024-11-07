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

package com.phonepe.drove.controller.utils;

import com.google.common.base.Joiner;
import com.google.common.base.Strings;
import com.phonepe.drove.common.CommonUtils;
import com.phonepe.drove.common.net.HttpCaller;
import com.phonepe.drove.controller.config.ControllerOptions;
import com.phonepe.drove.controller.engine.ControllerRetrySpecFactory;
import com.phonepe.drove.controller.resourcemgmt.ClusterResourcesDB;
import com.phonepe.drove.controller.resourcemgmt.ExecutorHostInfo;
import com.phonepe.drove.controller.statedb.ApplicationInstanceInfoDB;
import com.phonepe.drove.controller.statedb.LocalServiceStateDB;
import com.phonepe.drove.controller.statedb.TaskDB;
import com.phonepe.drove.models.api.ApiResponse;
import com.phonepe.drove.models.application.ApplicationSpec;
import com.phonepe.drove.models.application.MountedVolume;
import com.phonepe.drove.models.application.devices.DeviceSpec;
import com.phonepe.drove.models.application.placement.PlacementPolicy;
import com.phonepe.drove.models.application.placement.PlacementPolicyVisitor;
import com.phonepe.drove.models.application.placement.policies.*;
import com.phonepe.drove.models.application.requirements.CPURequirement;
import com.phonepe.drove.models.application.requirements.MemoryRequirement;
import com.phonepe.drove.models.application.requirements.ResourceRequirementVisitor;
import com.phonepe.drove.models.common.HTTPCallSpec;
import com.phonepe.drove.models.config.ConfigSpec;
import com.phonepe.drove.models.config.ConfigSpecVisitorAdapter;
import com.phonepe.drove.models.config.impl.ControllerHttpFetchConfigSpec;
import com.phonepe.drove.models.config.impl.InlineConfigSpec;
import com.phonepe.drove.models.info.nodedata.ControllerNodeData;
import com.phonepe.drove.models.info.nodedata.ExecutorNodeData;
import com.phonepe.drove.models.info.nodedata.NodeDataVisitor;
import com.phonepe.drove.models.instance.InstanceInfo;
import com.phonepe.drove.models.instance.InstanceState;
import com.phonepe.drove.models.instance.LocalServiceInstanceState;
import com.phonepe.drove.models.interfaces.DeployedInstanceInfo;
import com.phonepe.drove.models.interfaces.DeployedInstanceInfoVisitor;
import com.phonepe.drove.models.interfaces.DeploymentSpec;
import com.phonepe.drove.models.interfaces.DeploymentSpecVisitor;
import com.phonepe.drove.models.localservice.LocalServiceInstanceInfo;
import com.phonepe.drove.models.localservice.LocalServiceSpec;
import com.phonepe.drove.models.operation.*;
import com.phonepe.drove.models.operation.localserviceops.*;
import com.phonepe.drove.models.operation.ops.*;
import com.phonepe.drove.models.operation.taskops.TaskCreateOperation;
import com.phonepe.drove.models.operation.taskops.TaskKillOperation;
import com.phonepe.drove.models.task.TaskSpec;
import com.phonepe.drove.models.taskinstance.TaskInfo;
import com.phonepe.drove.models.taskinstance.TaskState;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import dev.failsafe.Failsafe;
import dev.failsafe.RetryPolicy;

import javax.ws.rs.core.Response;
import java.util.*;
import java.util.function.Supplier;

import static com.phonepe.drove.controller.utils.StateCheckStatus.*;

/**
 *
 */
@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class ControllerUtils {

    public static String deployableObjectId(DeploymentSpec deploymentSpec) {
        return deploymentSpec.accept(new DeploymentSpecVisitor<>() {
            @Override
            public String visit(ApplicationSpec applicationSpec) {
                return Strings.isNullOrEmpty(applicationSpec.getName()) || Strings.isNullOrEmpty(applicationSpec.getVersion())
                       ? null
                       : applicationSpec.getName() + "-" + applicationSpec.getVersion();
            }

            @Override
            public String visit(TaskSpec taskSpec) {
                return taskSpec.getSourceAppName() + "-" + taskSpec.getTaskId();
            }

            @Override
            public String visit(LocalServiceSpec localServiceSpec) {

                return Strings.isNullOrEmpty(localServiceSpec.getName()) || Strings.isNullOrEmpty(localServiceSpec.getVersion())
                       ? null
                       : localServiceSpec.getName() + "-" + localServiceSpec.getVersion();
            }
        });
    }

    public static boolean ensureInstanceState(
            final LocalServiceStateDB instanceInfoDB,
            ClusterOpSpec clusterOpSpec,
            String serviceId,
            String instanceId,
            LocalServiceInstanceState required,
            ControllerRetrySpecFactory retrySpecFactory) {
        val retryPolicy =
                CommonUtils.<StateCheckStatus>policy(
                        retrySpecFactory.instanceStateCheckRetrySpec(clusterOpSpec.getTimeout().toMilliseconds()),
                        MISMATCH::equals);
        try {
            val status = waitForState(
                    () -> ensureAppInstanceState(currentInstanceInfo(instanceInfoDB, serviceId, instanceId), required),
                    retryPolicy);
            if (status.equals(MATCH)) {
                return true;
            }
            else {
                val curr = currentInstanceInfo(instanceInfoDB, serviceId, instanceId);
                if (null == curr) {
                    log.error("No instance info found at all for: {}/{}", serviceId, instanceId);
                }
                else {
                    if (status.equals(MISMATCH)) {
                        log.error("Looks like app instance {}/{} is stuck in state: {}. Detailed instance data: {}}",
                                  serviceId, instanceId, curr.getState(), curr);
                    }
                    else {
                        log.error("Looks like app instance {}/{} has failed permanently and reached state: {}." +
                                          " Detailed instance data: {}}",
                                  serviceId,
                                  instanceId,
                                  curr.getState(),
                                  curr);
                    }
                }
            }
        }
        catch (Exception e) {
            log.error("Error starting instance: " + serviceId + "/" + instanceId, e);
        }
        return false;
    }

    public static boolean ensureInstanceState(
            final ApplicationInstanceInfoDB instanceInfoDB,
            ClusterOpSpec clusterOpSpec,
            String appId,
            String instanceId,
            InstanceState required,
            ControllerRetrySpecFactory retrySpecFactory) {
        val retryPolicy =
                CommonUtils.<StateCheckStatus>policy(
                        retrySpecFactory.instanceStateCheckRetrySpec(clusterOpSpec.getTimeout().toMilliseconds()),
                        MISMATCH::equals);
        try {
            val status = waitForState(
                    () -> ensureAppInstanceState(currentInstanceInfo(instanceInfoDB, appId, instanceId), required),
                    retryPolicy);
            if (status.equals(MATCH)) {
                return true;
            }
            else {
                val curr = currentInstanceInfo(instanceInfoDB, appId, instanceId);
                if (null == curr) {
                    log.error("No instance info found at all for: {}/{}", appId, instanceId);
                }
                else {
                    if (status.equals(MISMATCH)) {
                        log.error("Looks like app instance {}/{} is stuck in state: {}. Detailed instance data: {}}",
                                  appId, instanceId, curr.getState(), curr);
                    }
                    else {
                        log.error("Looks like app instance {}/{} has failed permanently and reached state: {}." +
                                          " Detailed instance data: {}}",
                                  appId,
                                  instanceId,
                                  curr.getState(),
                                  curr);
                    }
                }
            }
        }
        catch (Exception e) {
            log.error("Error starting instance: " + appId + "/" + instanceId, e);
        }
        return false;
    }

    public static boolean ensureTaskState(
            final TaskDB taskDB,
            ClusterOpSpec clusterOpSpec,
            String sourceAppName,
            String taskId,
            TaskState required,
            ControllerRetrySpecFactory retrySpecFactory) {
        val retryPolicy =
                CommonUtils.<StateCheckStatus>policy(
                        retrySpecFactory.instanceStateCheckRetrySpec(clusterOpSpec.getTimeout().toMilliseconds()),
                        MISMATCH::equals);
        try {
            val status = waitForState(
                    () -> ensureTaskState(currentTaskInfo(taskDB, sourceAppName, taskId), required), retryPolicy);
            if (status.equals(MATCH)) {
                return true;
            }
            else {
                val curr = currentTaskInfo(taskDB, sourceAppName, taskId);
                if (null == curr) {
                    log.error("No instance info found at all for: {}/{}", sourceAppName, taskId);
                }
                else {
                    if (status.equals(MISMATCH)) {
                        log.error("Looks like task {}/{} is stuck in state: {}. Detailed instance data: {}}",
                                  sourceAppName, taskId, curr.getState(), curr);
                    }
                    else {
                        log.error(
                                "Looks like task {}/{} has failed permanently and reached state: {}. Detailed " +
                                        "instance " +
                                        "data: {}}",
                                sourceAppName,
                                taskId,
                                curr.getState(),
                                curr);
                    }
                }
            }
        }
        catch (Exception e) {
            log.error("Error starting instance: " + sourceAppName + "/" + taskId, e);
        }
        return false;
    }

    public static <O extends ApplicationOperation> O safeCast(
            final ApplicationOperation applicationOperation,
            final Class<O> clazz) {
        val obj = applicationOperation.getClass().equals(clazz)
                  ? clazz.cast(applicationOperation)
                  : null;
        Objects.requireNonNull(obj,
                               "Cannot cast op " + applicationOperation.getClass()
                                       .getSimpleName() + " to " + clazz.getSimpleName());
        return obj;
    }

    public static <O extends LocalServiceOperation> O safeCast(
            final LocalServiceOperation operation,
            final Class<O> clazz) {
        val obj = operation.getClass().equals(clazz)
                  ? clazz.cast(operation)
                  : null;
        Objects.requireNonNull(obj,
                               "Cannot cast op " + operation.getClass()
                                       .getSimpleName() + " to " + clazz.getSimpleName());
        return obj;
    }

    public static <O extends TaskOperation> O safeCast(
            final TaskOperation taskOperation,
            final Class<O> clazz) {
        val obj = taskOperation.getClass().equals(clazz)
                  ? clazz.cast(taskOperation)
                  : null;
        Objects.requireNonNull(obj,
                               "Cannot cast op " + taskOperation.getClass()
                                       .getSimpleName() + " to " + clazz.getSimpleName());
        return obj;
    }

    private static LocalServiceInstanceInfo currentInstanceInfo(
            final LocalServiceStateDB instanceInfoDB,
            String serviceId,
            String instanceId) {
        return instanceInfoDB.instance(serviceId, instanceId).orElse(null);
    }

    private static InstanceInfo currentInstanceInfo(
            final ApplicationInstanceInfoDB instanceInfoDB,
            String appId,
            String instanceId) {
        return instanceInfoDB.instance(appId, instanceId).orElse(null);
    }

    private static TaskInfo currentTaskInfo(
            final TaskDB taskDB,
            String sourceAppName,
            String taskId) {
        return taskDB.task(sourceAppName, taskId).orElse(null);
    }

    private static<T extends Enum<T>> StateCheckStatus ensureAppInstanceState(
            final LocalServiceInstanceInfo instanceInfo,
            final T instanceState) {
        if (null != instanceInfo) {
            val currState = instanceInfo.getState();
            log.trace("Local Service Instance state for {}/{}: {}",
                      instanceInfo.getServiceId(), instanceInfo.getInstanceId(), currState);
            if (currState == instanceState) {
                log.info("Local Service Instance {}/{} reached desired state: {}",
                         instanceInfo.getServiceId(),
                         instanceInfo.getInstanceId(),
                         instanceState);
                return MATCH;
            }
            if (currState.isTerminal()) { //Useless to wait if it has died anyway
                return MISMATCH_NONRECOVERABLE;
            }
        }
        return MISMATCH;
    }

    private static<T extends Enum<T>> StateCheckStatus ensureAppInstanceState(
            final InstanceInfo instanceInfo,
            final T instanceState) {
        if (null != instanceInfo) {
            val currState = instanceInfo.getState();
            log.trace("Instance state for {}/{}: {}",
                      instanceInfo.getAppId(), instanceInfo.getInstanceId(), currState);
            if (currState == instanceState) {
                log.info("Instance {}/{} reached desired state: {}",
                         instanceInfo.getAppId(),
                         instanceInfo.getInstanceId(),
                         instanceState);
                return MATCH;
            }
            if (currState.isTerminal()) { //Useless to wait if it has died anyway
                return MISMATCH_NONRECOVERABLE;
            }
        }
        return MISMATCH;
    }

    private static StateCheckStatus ensureTaskState(final TaskInfo instanceInfo, final TaskState instanceState) {
        if (null != instanceInfo) {
            val currState = instanceInfo.getState();
            log.trace("Task state for {}/{}: {}",
                      instanceInfo.getSourceAppName(), instanceInfo.getTaskId(), currState);
            if (currState == instanceState) {
                log.info("Task {}/{} reached desired state: {}",
                         instanceInfo.getSourceAppName(),
                         instanceInfo.getTaskId(),
                         instanceState);
                return MATCH;
            }
            if (currState.isTerminal()) { //Useless to wait if it has died anyway
                return MISMATCH_NONRECOVERABLE;
            }
        }
        return MISMATCH;
    }

    public static String deployableObjectId(final ApplicationOperation operation) {
        return operation.accept(new ApplicationOperationVisitor<>() {
            @Override
            public String visit(ApplicationCreateOperation create) {
                return deployableObjectId(create.getSpec());
            }

            @Override
            public String visit(ApplicationDestroyOperation destroy) {
                return destroy.getAppId();
            }

            @Override
            public String visit(ApplicationStartInstancesOperation deploy) {
                return deploy.getAppId();
            }

            @Override
            public String visit(ApplicationStopInstancesOperation stopInstances) {
                return stopInstances.getAppId();
            }

            @Override
            public String visit(ApplicationScaleOperation scale) {
                return scale.getAppId();
            }

            @Override
            public String visit(ApplicationReplaceInstancesOperation replaceInstances) {
                return replaceInstances.getAppId();
            }

            @Override
            public String visit(ApplicationSuspendOperation suspend) {
                return suspend.getAppId();
            }

            @Override
            public String visit(ApplicationRecoverOperation recover) {
                return recover.getAppId();
            }

        });
    }

    public static String deployableObjectId(final TaskOperation operation) {
        return operation.accept(new TaskOperationVisitor<>() {
            @Override
            public String visit(TaskCreateOperation create) {
                return deployableObjectId(create.getSpec());
            }

            @Override
            public String visit(TaskKillOperation kill) {
                return kill.getTaskId();
            }
        });
    }

    public static long usedMemory(ExecutorHostInfo executor) {
        return executor.getNodeData()
                .accept(new NodeDataVisitor<>() {
                    @Override
                    public Long visit(ControllerNodeData controllerData) {
                        return 0L;
                    }

                    @Override
                    public Long visit(ExecutorNodeData executorData) {
                        return executorData.getState().getMemory().getUsedMemory().values()
                                .stream()
                                .mapToLong(v -> v)
                                .sum();
                    }
                });
    }

    public static long freeMemory(ExecutorHostInfo executor) {
        return executor.getNodeData()
                .accept(new NodeDataVisitor<>() {
                    @Override
                    public Long visit(ControllerNodeData controllerData) {
                        return 0L;
                    }

                    @Override
                    public Long visit(ExecutorNodeData executorData) {
                        return executorData.getState().getMemory().getFreeMemory().values()
                                .stream()
                                .mapToLong(v -> v)
                                .sum();
                    }
                });
    }

    public static int freeCores(ExecutorHostInfo executor) {
        return executor.getNodeData()
                .accept(new NodeDataVisitor<>() {
                    @Override
                    public Integer visit(ControllerNodeData controllerData) {
                        return 0;
                    }

                    @Override
                    public Integer visit(ExecutorNodeData executorData) {
                        return executorData.getState().getCpus().getFreeCores().values()
                                .stream()
                                .mapToInt(Set::size)
                                .sum();
                    }
                });
    }

    public static int usedCores(ExecutorHostInfo executor) {
        return executor.getNodeData()
                .accept(new NodeDataVisitor<>() {
                    @Override
                    public Integer visit(ControllerNodeData controllerData) {
                        return 0;
                    }

                    @Override
                    public Integer visit(ExecutorNodeData executorData) {
                        return executorData.getState().getCpus().getUsedCores().values()
                                .stream()
                                .mapToInt(Set::size)
                                .sum();
                    }
                });
    }

    public static <T> Response ok(final T data) {
        return Response.ok(ApiResponse.success(data)).build();
    }

    public static Response commandValidationFailure(String message) {
        return commandValidationFailure(List.of(message));
    }

    public static Response commandValidationFailure(List<String> messages) {
        return ControllerUtils.badRequest(Map.of("validationErrors", messages),
                                          "Command validation failure");
    }

    public static <T> Response badRequest(T data, String message) {
        return Response.status(Response.Status.BAD_REQUEST)
                .entity(ApiResponse.failure(data, message))
                .build();
    }

    public static String deployableObjectId(DeployedInstanceInfo instanceInfo) {
        return instanceInfo.accept(new DeployedInstanceInfoVisitor<>() {
            @Override
            public String visit(InstanceInfo applicationInstanceInfo) {
                return applicationInstanceInfo.getInstanceId();
            }

            @Override
            public String visit(TaskInfo taskInfo) {
                return taskInfo.getTaskId();
            }

            @Override
            public String visit(LocalServiceInstanceInfo localServiceInstanceInfo) {
                return localServiceInstanceInfo.getInstanceId();
            }
        });
    }

    public static List<String> ensureWhitelistedVolumes(
            Collection<MountedVolume> volumes, ControllerOptions controllerOptions) {
        val whitelistedDirs = Objects.requireNonNullElse(
                controllerOptions.getAllowedMountDirs(),
                List.<String>of());
        if (null != volumes && !whitelistedDirs.isEmpty()) {
            return volumes.stream()
                    .filter(volume -> whitelistedDirs.stream()
                            .noneMatch(dir -> volume.getPathOnHost().startsWith(dir)))
                    .map(volume -> "Volume mount requested on non whitelisted host directory: "
                            + volume.getPathOnHost())
                    .toList();
        }
        return List.of();
    }

    public static List<String> ensureCmdlArgs(
            List<String> args, ControllerOptions controllerOptions) {
        val argsDisabled = Objects.requireNonNullElse(controllerOptions.getDisableCmdlArgs(), false);
        val argList = Objects.requireNonNullElse(args, List.<String>of());
        if (argsDisabled && !argList.isEmpty()) {
            return List.of("Passing command line to containers is disabled on this cluster");
        }
        if (Joiner.on(" ").join(argList).length() > 2048) {
            return List.of("Maximum combined length of command line arguments can be 2048");
        }
        return List.of();
    }

    public static List<String> checkDeviceDisabled(
            List<DeviceSpec> devices, ControllerOptions controllerOptions) {
        val deviceEnabled = Objects.requireNonNullElse(controllerOptions.getEnableRawDeviceAccess(), false);
        if (!deviceEnabled && null != devices && !devices.isEmpty()) {
            return List.of("Device access is disabled. " +
                                   "To enable, set enableRawDeviceAccess: true in controller options.");
        }
        return List.of();
    }

    public static List<ConfigSpec> translateConfigSpecs(final List<ConfigSpec> configs, final HttpCaller httpCaller) {
        return Objects.requireNonNullElse(configs, List.<ConfigSpec>of())
                .stream()
                .map(configSpec -> configSpec.accept(new ConfigSpecVisitorAdapter<>(configSpec) {
                    @Override
                    public ConfigSpec visit(ControllerHttpFetchConfigSpec controllerHttpFetchConfig) {
                        return new InlineConfigSpec(
                                configSpec.getLocalFilename(),
                                httpCaller.execute(configSpec.accept(new ConfigSpecVisitorAdapter<>() {
                                    @Override
                                    public HTTPCallSpec visit(ControllerHttpFetchConfigSpec controllerHttpFetchConfig) {
                                        return controllerHttpFetchConfig.getHttp();
                                    }
                                })));
                    }
                }))
                .toList();
    }

    @SuppressWarnings("java:S1874")
    public static StateCheckStatus waitForState(
            Supplier<StateCheckStatus> checker,
            RetryPolicy<StateCheckStatus> retryPolicy) {
        return Failsafe.with(List.of(retryPolicy))
                .onComplete(e -> {
                    val failure = e.getException();
                    if (null != failure) {
                        log.error("Error starting instance: {}", failure.getMessage());
                    }
                })
                .get(checker::get);
    }

    public static void checkResources(
            final ClusterResourcesDB clusterResourcesDB,
            final DeploymentSpec spec,
            final long requiredInstances,
            final List<String> errors) {
        val executors = clusterResourcesDB.currentSnapshot(true);
        var freeCores = 0;
        var freeMemory = 0L;
        for (val exec : executors) {
            freeCores += ControllerUtils.freeCores(exec);
            freeMemory += ControllerUtils.freeMemory(exec);
        }

        val requiredCoresPerInstance = spec.getResources()
                .stream()
                .mapToInt(r -> r.accept(new ResourceRequirementVisitor<>() {
                    @Override
                    public Integer visit(CPURequirement cpuRequirement) {
                        return (int) cpuRequirement.getCount();
                    }

                    @Override
                    public Integer visit(MemoryRequirement memoryRequirement) {
                        return 0;
                    }
                }))
                .sum();
        val requiredCores = requiredInstances * requiredCoresPerInstance;
        val requiredMemPerInstance = spec.getResources()
                .stream()
                .mapToLong(r -> r.accept(new ResourceRequirementVisitor<>() {
                    @Override
                    public Long visit(CPURequirement cpuRequirement) {
                        return 0L;
                    }

                    @Override
                    public Long visit(MemoryRequirement memoryRequirement) {
                        return memoryRequirement.getSizeInMB();
                    }
                }))
                .sum();
        val requiredMem = requiredInstances * requiredMemPerInstance;
        if (requiredCores > freeCores) {
            errors.add("Cluster does not have enough CPU. Required: " + requiredCores + " " +
                               "Available: " + freeCores);
        }
        if (requiredMem > freeMemory) {
            errors.add("Cluster does not have enough Memory. Required: " + requiredMem + " " +
                               "Available: " + freeMemory);
        }
        val maxAvailablePhysicalCoresPerNode = executors.stream()
                .map(e -> e.getNodeData().getState().getLayout())
                .filter(Objects::nonNull)
                .flatMap(physicalLayout -> physicalLayout.getCores().values().stream().map(Set::size))
                .mapToInt(Integer::intValue)
                .max()
                .orElse(Integer.MAX_VALUE);
        if (maxAvailablePhysicalCoresPerNode < requiredCoresPerInstance) {
            errors.add("Required cores exceeds the maximum core available on a single " +
                               "NUMA node in the cluster. Required: " + requiredCores
                               + " Max: " + maxAvailablePhysicalCoresPerNode);
        }
    }


    public static boolean hasLocalPolicy(final PlacementPolicy policy) {
        return policy.accept(new PlacementPolicyVisitor<Boolean>() {
            @Override
            public Boolean visit(OnePerHostPlacementPolicy onePerHost) {
                return false;
            }

            @Override
            public Boolean visit(MaxNPerHostPlacementPolicy maxNPerHost) {
                return false;
            }

            @Override
            public Boolean visit(MatchTagPlacementPolicy matchTag) {
                return true;
            }

            @Override
            public Boolean visit(NoTagPlacementPolicy noTag) {
                return false;
            }

            @Override
            public Boolean visit(RuleBasedPlacementPolicy ruleBased) {
                return false;
            }

            @Override
            public Boolean visit(AnyPlacementPolicy anyPlacementPolicy) {
                return false;
            }

            @Override
            public Boolean visit(CompositePlacementPolicy compositePlacementPolicy) {
                return compositePlacementPolicy.getPolicies()
                        .stream()
                        .anyMatch(policy -> hasLocalPolicy(policy));
            }

            @Override
            public Boolean visit(LocalPlacementPolicy localPlacementPolicy) {
                return true;
            }
        });
    }

    public static ClusterResourcesDB.ClusterResourcesSummary summarizeResources(final List<ExecutorHostInfo> executors) {
        var freeCores = 0;
        var usedCores = 0;
        var freeMemory = 0L;
        var usedMemory = 0L;
        for (val executor : executors) {
            usedCores += usedCores(executor);
            freeCores += freeCores(executor);
            freeMemory += freeMemory(executor);
            usedMemory += usedMemory(executor);
        }
        return new ClusterResourcesDB.ClusterResourcesSummary(executors.size(),
                                                              freeCores,
                                                              usedCores,
                                                              freeCores + usedCores,
                                                              freeMemory,
                                                              usedMemory,
                                                              freeMemory + usedMemory);
    }

    public static String deployableObjectId(LocalServiceOperation operation) {
        return operation.accept(new LocalServiceOperationVisitor<>() {
            @Override
            public String visit(LocalServiceCreateOperation localServiceCreateOperation) {
                return ControllerUtils.deployableObjectId(localServiceCreateOperation.getSpec());
            }

            @Override
            public String visit(LocalServiceScaleOperation localServiceScaleOperation) {
                return localServiceScaleOperation.getServiceId();
            }

            @Override
            public String visit(LocalServiceDeactivateOperation localServiceDeactivateOperation) {
                return localServiceDeactivateOperation.getServiceId();
            }

            @Override
            public String visit(LocalServiceRestartOperation localServiceRestartOperation) {
                return localServiceRestartOperation.getServiceId();
            }

            @Override
            public String visit(LocalServiceUpdateOperation localServiceUpdateOperation) {
                return localServiceUpdateOperation.getServiceId();
            }

            @Override
            public String visit(LocalServiceDestroyOperation localServiceDestroyOperation) {
                return localServiceDestroyOperation.getServiceId();
            }

            @Override
            public String visit(LocalServiceActivateOperation localServiceActivateOperation) {
                return localServiceActivateOperation.getServiceId();
            }

            @Override
            public String visit(LocalServiceReplaceInstancesOperation localServiceReplaceInstancesOperation) {
                return localServiceReplaceInstancesOperation.getServiceId();
            }

            @Override
            public String visit(LocalServiceStopInstancesOperation localServiceStopInstancesOperation) {
                return localServiceStopInstancesOperation.getServiceId();
            }
        });
    }
}
