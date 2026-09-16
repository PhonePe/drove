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

package com.phonepe.drove.controller.resourcemgmt;

import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import javax.inject.Inject;
import javax.inject.Singleton;

import com.google.common.base.Strings;
import com.google.common.collect.Sets;
import com.phonepe.drove.controller.rule.RuleEvaluator;
import com.phonepe.drove.controller.statedb.ApplicationInstanceInfoDB;
import com.phonepe.drove.controller.statedb.LocalServiceStateDB;
import com.phonepe.drove.controller.statedb.TaskDB;
import com.phonepe.drove.controller.utils.ControllerUtils;
import com.phonepe.drove.models.application.ApplicationSpec;
import com.phonepe.drove.models.application.placement.PlacementPolicy;
import com.phonepe.drove.models.application.placement.PlacementPolicyVisitor;
import com.phonepe.drove.models.application.placement.policies.AnyPlacementPolicy;
import com.phonepe.drove.models.application.placement.policies.CompositePlacementPolicy;
import com.phonepe.drove.models.application.placement.policies.LocalPlacementPolicy;
import com.phonepe.drove.models.application.placement.policies.MatchTagPlacementPolicy;
import com.phonepe.drove.models.application.placement.policies.MaxNPerHostPlacementPolicy;
import com.phonepe.drove.models.application.placement.policies.NoTagPlacementPolicy;
import com.phonepe.drove.models.application.placement.policies.OnePerHostPlacementPolicy;
import com.phonepe.drove.models.application.placement.policies.RuleBasedPlacementPolicy;
import com.phonepe.drove.models.application.requirements.ResourceType;
import com.phonepe.drove.models.info.nodedata.ExecutorState;
import com.phonepe.drove.models.info.resources.NodeSelectionContext;
import com.phonepe.drove.models.info.resources.allocation.AllocatedExecutorNode;
import com.phonepe.drove.models.info.resources.allocation.CPUAllocation;
import com.phonepe.drove.models.info.resources.allocation.GPUAllocation;
import com.phonepe.drove.models.info.resources.allocation.InstanceResourceAllocation;
import com.phonepe.drove.models.info.resources.allocation.MemoryAllocation;
import com.phonepe.drove.models.info.resources.allocation.ResourceAllocation;
import com.phonepe.drove.models.instance.InstanceInfo;
import com.phonepe.drove.models.instance.InstanceState;
import com.phonepe.drove.models.instance.LocalServiceInstanceState;
import com.phonepe.drove.models.interfaces.DeployedInstanceInfo;
import com.phonepe.drove.models.interfaces.DeployedInstanceInfoVisitor;
import com.phonepe.drove.models.interfaces.DeploymentSpec;
import com.phonepe.drove.models.interfaces.DeploymentSpecVisitor;
import com.phonepe.drove.models.localservice.LocalServiceInstanceInfo;
import com.phonepe.drove.models.localservice.LocalServiceSpec;
import com.phonepe.drove.models.operation.rule.RuleCallStatus;
import com.phonepe.drove.models.task.TaskSpec;
import com.phonepe.drove.models.taskinstance.TaskInfo;
import com.phonepe.drove.models.taskinstance.TaskState;

import io.appform.functionmetrics.MonitoredFunction;
import lombok.NonNull;
import lombok.val;
import lombok.extern.slf4j.Slf4j;

/**
 *
 */
@Singleton
@Slf4j
public class DefaultInstanceScheduler implements InstanceScheduler {
    private static final Set<InstanceState> RESOURCE_CONSUMING_INSTANCE_STATES = EnumSet.of(InstanceState.PENDING,
                                                                                            InstanceState.PROVISIONING,
                                                                                            InstanceState.STARTING,
                                                                                            InstanceState.UNREADY,
                                                                                            InstanceState.READY,
                                                                                            InstanceState.HEALTHY,
                                                                                            InstanceState.UNHEALTHY,
                                                                                            InstanceState.DEPROVISIONING,
                                                                                            InstanceState.STOPPING);
    private static final Set<TaskState> RESOURCE_CONSUMING_TASK_STATES = EnumSet.of(TaskState.PENDING,
                                                                                    TaskState.PROVISIONING,
                                                                                    TaskState.STARTING,
                                                                                    TaskState.RUNNING,
                                                                                    TaskState.RUN_COMPLETED,
                                                                                    TaskState.DEPROVISIONING);
    private static final Set<LocalServiceInstanceState> RESOURCE_CONSUMING_LOCAL_SERVICE_INSTANCE_STATES = EnumSet.of(
            LocalServiceInstanceState.PENDING,
            LocalServiceInstanceState.PROVISIONING,
            LocalServiceInstanceState.STARTING,
            LocalServiceInstanceState.UNREADY,
            LocalServiceInstanceState.READY,
            LocalServiceInstanceState.HEALTHY,
            LocalServiceInstanceState.UNHEALTHY,
            LocalServiceInstanceState.DEPROVISIONING,
            LocalServiceInstanceState.STOPPING);
    private final ApplicationInstanceInfoDB instanceInfoDB;
    private final TaskDB taskDB;
    private final LocalServiceStateDB localServiceStateDB;
    private final ClusterResourcesDB clusterResourcesDB;
    private final RuleEvaluator ruleEvaluator;
    //Map of sessionId -> [executorId -> [ instanceId -> resources]]]
    private final Map<String, Map<String, Map<String, InstanceResourceAllocation>>> schedulingSessionData =
            new ConcurrentHashMap<>();

    @Inject
    public DefaultInstanceScheduler(
            ApplicationInstanceInfoDB instanceInfoDB,
            TaskDB taskDB,
            LocalServiceStateDB localServiceStateDB,
      ClusterResourcesDB clusterResourcesDB,
            RuleEvaluator ruleEvaluator) {
        this.instanceInfoDB = instanceInfoDB;
        this.taskDB = taskDB;
        this.localServiceStateDB = localServiceStateDB;
        this.clusterResourcesDB = clusterResourcesDB;
        this.ruleEvaluator = ruleEvaluator;
    }

    @Override
    @MonitoredFunction
    public synchronized Optional<AllocatedExecutorNode> schedule(
            @NonNull final String schedulingSessionId,
            final String instanceId,
            final DeploymentSpec deploymentSpec,
            final PlacementPolicy placementPolicy,
            final Set<ExecutorState> allowedStates) {
        return place(schedulingSessionId,
                     List.of(instanceId),
                     deploymentSpec,
                     placementPolicy,
                     allowedStates,
                     schedulingSessionData,
                     false)
            .stream()
            .findAny();
    }

    @Override
    @MonitoredFunction
    public List<AllocatedExecutorNode> simulatePlacement(
            final DeploymentSpec deploymentSpec,
            final PlacementPolicy requiredPlacementPolicy,
            final int numInstances) {
        val placementPolicy = Objects.requireNonNullElseGet(requiredPlacementPolicy,
                () -> ControllerUtils.computeEffectivePlacementPolicy(deploymentSpec));
        val schedulingSessionId = UUID.randomUUID().toString();
        val localSessionData = new ConcurrentHashMap<>(Map.of(schedulingSessionId, clusterSnapshot(deploymentSpec)));
        val instanceIds = IntStream.rangeClosed(1, numInstances)
            .mapToObj(i -> "%s-simulation-instance-%d".formatted(deploymentSpec.getType().name().toLowerCase(), i))
            .toList();
        return place(schedulingSessionId,
                     instanceIds,
                     deploymentSpec,
                     placementPolicy,
                     EnumSet.of(ExecutorState.ACTIVE),
                     localSessionData,
                     true);
    }

    @Override
    @MonitoredFunction
    public synchronized void finaliseSession(final String schedulingSessionId) {
        schedulingSessionData.remove(schedulingSessionId);
        log.info("FINALIZED::SID: {}", schedulingSessionId);

    }

    @Override
    @MonitoredFunction
    public synchronized boolean discardAllocation(
            String schedulingSessionId,
            String instanceId,
            AllocatedExecutorNode node) {
        schedulingSessionData.computeIfPresent(schedulingSessionId, (id, executors) -> {
            var executorId = executorId(instanceId, node, executors);
            if (!Strings.isNullOrEmpty(executorId)) {
                val updatedAllocation = executors.compute(
                        executorId,
                        (eId, allocation) -> {
                            if (null != allocation) {
                                val removed = allocation.remove(instanceId);
                                if (removed != null) {
                                    clusterResourcesDB.deselectNode(removed.getExecutorId(),
                                                                    removed.getCpu(),
                                                                    removed.getMemory(),
                                            removed.getGpu());
                                    log.info("Relinquished resources for {}/{}",
                                             removed.getExecutorId(), removed.getInstanceId());
                                }
                            }
                            return allocation;
                        });
                log.info("POST_DISCARD::SID: {} exec id: {} count: {}",
                         schedulingSessionId,
                         executorId,
                         null == updatedAllocation ? 0 : updatedAllocation.size());
            }
            return executors;
        });
        return true;
    }

    private static String executorId(
            String instanceId,
            AllocatedExecutorNode node,
            Map<String, Map<String, InstanceResourceAllocation>> executors) {
        return null != node
               ? node.getExecutorId()
               : executors.values()
                       .stream().map(instances -> instances.get(instanceId))
                       .filter(Objects::nonNull)
                       .map(InstanceResourceAllocation::getExecutorId)
                       .findAny()
                       .orElse(null);
    }

    private List<AllocatedExecutorNode> place(
            @NonNull final String schedulingSessionId,
            final List<String> instanceIds,
            final DeploymentSpec deploymentSpec,
            final PlacementPolicy placementPolicy,
            final Set<ExecutorState> allowedStates,
            final Map<String, Map<String, Map<String, InstanceResourceAllocation>>> providedSessionData,
            final boolean simulation) {
        val sessionData = providedSessionData.computeIfAbsent(
                schedulingSessionId,
                id -> {
                    log.info("CREATE::SID: {}", schedulingSessionId);
                    return clusterSnapshot(deploymentSpec);
                });
        return instanceIds.stream()
            .map(instanceId -> {
                val allocatedExecutorNode = clusterResourcesDB.selectNodes(
                    deploymentSpec.getResources(),
                    allowedStates,
                    filterContext -> validateNode(
                        filterContext,
                        schedulingSessionId,
                        instanceId,
                        placementPolicy,
                        sessionData,
                        deploymentSpec),
                    simulation,
                    true)
                    .orElse(null);
                if(null == allocatedExecutorNode) {
                    return null;
                }
                //If a node is found, add it to the list of allocated nodes for this session
                //Next time a request for this session comes, this will ensure that allocations done in current session
                //Are taken into consideration
                providedSessionData.computeIfPresent(
                    schedulingSessionId,
                    (sid, executorAllocations) -> {
                        val executorId = allocatedExecutorNode.getExecutorId();
                        val currentAllocations = executorAllocations.computeIfAbsent(executorId, eId -> new HashMap<>());
                        currentAllocations.put(instanceId,
                                               new InstanceResourceAllocation(executorId,
                                                                              instanceId,
                                                                              allocatedExecutorNode.getCpu(),
                                                                              allocatedExecutorNode.getMemory(),
                                                                              allocatedExecutorNode.getGpu()));
                        log.info("POST_ALLOC::SID: {} exec id: {} count: {}",
                                 sid, executorId, currentAllocations.size());
                        return executorAllocations;
                    });
                return allocatedExecutorNode;
            })
            .filter(Objects::nonNull)
            .toList();
    }

    private Map<String, Map<String, InstanceResourceAllocation>> clusterSnapshot(DeploymentSpec deploymentSpec) {
        return deploymentSpec.accept(new DeploymentSpecVisitor<>() {
            @Override
            public Map<String, Map<String, InstanceResourceAllocation>> visit(ApplicationSpec applicationSpec) {
                return instanceInfoDB.instances(Set.of(ControllerUtils.deployableObjectId(applicationSpec)),
                                                RESOURCE_CONSUMING_INSTANCE_STATES,
                                                false)
                        .values()
                        .stream()
                        .flatMap(Collection::stream)
                        .map(info -> convert(info))
                        .collect(Collectors.groupingBy(InstanceResourceAllocation::getExecutorId,
                                                       Collectors.toMap(InstanceResourceAllocation::getInstanceId,
                                                                        Function.identity())));
            }

            @Override
            public Map<String, Map<String, InstanceResourceAllocation>> visit(TaskSpec taskSpec) {
                return taskDB.tasks(Set.of(taskSpec.getSourceAppName()), RESOURCE_CONSUMING_TASK_STATES, false)
                        .values()
                        .stream()
                        .flatMap(Collection::stream)
                        .map(info -> convert(info))
                        .collect(Collectors.groupingBy(InstanceResourceAllocation::getExecutorId,
                                                       Collectors.toMap(InstanceResourceAllocation::getInstanceId,
                                                                        Function.identity())));
            }

            @Override
            public Map<String, Map<String, InstanceResourceAllocation>> visit(LocalServiceSpec localServiceSpec) {
                return localServiceStateDB.instances(ControllerUtils.deployableObjectId(localServiceSpec),
                                                     RESOURCE_CONSUMING_LOCAL_SERVICE_INSTANCE_STATES, false)
                        .stream()
                        .map(info -> convert(info))
                        .collect(Collectors.groupingBy(InstanceResourceAllocation::getExecutorId,
                                                       Collectors.toMap(InstanceResourceAllocation::getInstanceId,
                                                                        Function.identity())));
            }
        });
    }

    private InstanceResourceAllocation convert(final DeployedInstanceInfo instanceInfo) {
        return instanceInfo.accept(new DeployedInstanceInfoVisitor<>() {
            @Override
            public InstanceResourceAllocation visit(InstanceInfo applicationInstanceInfo) {
                return new InstanceResourceAllocation(
                        applicationInstanceInfo.getExecutorId(),
                        applicationInstanceInfo.getInstanceId(),
                        (CPUAllocation) filterAllocation(applicationInstanceInfo.getResources(), ResourceType.CPU),
                        (MemoryAllocation) filterAllocation(applicationInstanceInfo.getResources(),
                                                            ResourceType.MEMORY),
                        (GPUAllocation) filterAllocation(applicationInstanceInfo.getResources(), ResourceType.GPU));
            }

            @Override
            public InstanceResourceAllocation visit(TaskInfo taskInfo) {
                return new InstanceResourceAllocation(
                        taskInfo.getExecutorId(),
                        taskInfo.getInstanceId(),
                        (CPUAllocation) filterAllocation(taskInfo.getResources(), ResourceType.CPU),
                        (MemoryAllocation) filterAllocation(taskInfo.getResources(), ResourceType.MEMORY),
                        (GPUAllocation) filterAllocation(taskInfo.getResources(), ResourceType.GPU));
            }

            @Override
            public InstanceResourceAllocation visit(LocalServiceInstanceInfo localServiceInstanceInfo) {
                return new InstanceResourceAllocation(
                        localServiceInstanceInfo.getExecutorId(),
                        localServiceInstanceInfo.getInstanceId(),
                        (CPUAllocation) filterAllocation(localServiceInstanceInfo.getResources(), ResourceType.CPU),
                        (MemoryAllocation) filterAllocation(localServiceInstanceInfo.getResources(),
                                                            ResourceType.MEMORY),
                        (GPUAllocation) filterAllocation(localServiceInstanceInfo.getResources(), ResourceType.GPU));
            }

            private static ResourceAllocation filterAllocation(
                    List<ResourceAllocation> applicationInstanceInfo,
                    ResourceType resourceType) {
                return Objects.requireNonNullElse(applicationInstanceInfo, List.<ResourceAllocation>of())
                        .stream()
                        .filter(resourceAllocation -> resourceAllocation.getType().equals(resourceType))
                        .findAny()
                        .orElse(null);
            }
        });
    }

    private boolean validateNode(
            final ClusterResourcesDB.FilterContext filterContext,
            final String sessionId,
            final String instanceId,
            final PlacementPolicy placementPolicy,
            Map<String, Map<String, InstanceResourceAllocation>> sessionLevelData,
            final DeploymentSpec deploymentSpec) {
        val allocatedExecutorId = filterContext.getAllocatedExecutorNode().getExecutorId();

        return placementPolicy.accept(new PlacementPolicyVisitor<>() {
            @Override
            public Boolean visit(OnePerHostPlacementPolicy onePerHost) {
                log.debug("Existing: {} allocated: {}", sessionLevelData.keySet(), allocatedExecutorId);
                val existing = sessionLevelData.get(allocatedExecutorId);
                return existing == null || existing.isEmpty();
            }

            @Override
            public Boolean visit(MaxNPerHostPlacementPolicy maxNPerHost) {
                val numExistingInstances = sessionLevelData.getOrDefault(allocatedExecutorId, Map.of()).size();
                log.debug("Existing Instances: {} on allocated executor: {}",
                          numExistingInstances, allocatedExecutorId);
                return numExistingInstances < maxNPerHost.getMax();
            }

            @Override
            public Boolean visit(MatchTagPlacementPolicy matchTag) {
                return Objects.requireNonNullElse(filterContext.getAllocatedExecutorNodeData().getTags(), Set.of()).contains(matchTag.getTag());
            }

            @Override
            public Boolean visit(NoTagPlacementPolicy noTag) {
                //Return false if node has any tag other than hostname
                return Sets.difference(Objects.requireNonNullElse(filterContext.getAllocatedExecutorNodeData().getTags(), Set.of()),
                                       Set.of(filterContext.getAllocatedExecutorNodeData().getHostname())).isEmpty();
            }

            @Override
            public Boolean visit(RuleBasedPlacementPolicy ruleBased) {
                val clusterSnapshot = filterContext.getLiveExecutors()
                               .stream()
                               .collect(Collectors.toMap(ExecutorHostInfo::getExecutorId,
                                                         ExecutorHostInfo::getNodeData));
                val context = NodeSelectionContext.builder()
                    .session(NodeSelectionContext.SchedulingSession.builder()
                           .sessionId(sessionId)
                            .existingAllocations(sessionLevelData)
                            .build())
                    .environment(NodeSelectionContext.SchedulingEnvironment.builder()
                            .liveExecutors(clusterSnapshot)
                            .variables(Map.copyOf(deploymentSpec.getEnv()))
                            .build())
                    .allocation(NodeSelectionContext.NodeAllocation.builder()
                            .spec(deploymentSpec)
                            .instanceId(instanceId)
                            .node(filterContext.getAllocatedExecutorNode())
                            .build())
                    .build();

                val evalResponse = ruleEvaluator.evaluate(ruleBased, context);
                return (RuleCallStatus.SUCCESS == evalResponse.getStatus() &&
                        evalResponse.isResult());
            }

            @Override
            public Boolean visit(AnyPlacementPolicy anyPlacementPolicy) {
                return true;
            }

            @Override
            public Boolean visit(CompositePlacementPolicy compositePlacementPolicy) {
                val combiner
                        = Objects.requireNonNullElse(compositePlacementPolicy.getCombiner(),
                                                     CompositePlacementPolicy.CombinerType.AND);
                val policiesStream = compositePlacementPolicy
                        .getPolicies()
                        .stream();
                return combiner == CompositePlacementPolicy.CombinerType.AND
                       ? policiesStream.allMatch(placementPolicy -> placementPolicy.accept(this))
                       : policiesStream.anyMatch(placementPolicy -> placementPolicy.accept(this));
            }

            @Override
            public Boolean visit(LocalPlacementPolicy localPlacementPolicy) {
                return true;
            }
        });
    }
}
