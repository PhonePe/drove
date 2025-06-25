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

import com.google.common.base.Strings;
import com.google.common.collect.Sets;
import com.phonepe.drove.controller.statedb.ApplicationInstanceInfoDB;
import com.phonepe.drove.controller.statedb.LocalServiceStateDB;
import com.phonepe.drove.controller.statedb.TaskDB;
import com.phonepe.drove.controller.utils.ControllerUtils;
import com.phonepe.drove.models.application.ApplicationSpec;
import com.phonepe.drove.models.application.placement.PlacementPolicy;
import com.phonepe.drove.models.application.placement.PlacementPolicyVisitor;
import com.phonepe.drove.models.application.placement.policies.*;
import com.phonepe.drove.models.application.requirements.ResourceType;
import com.phonepe.drove.models.info.nodedata.ExecutorState;
import com.phonepe.drove.models.info.resources.allocation.CPUAllocation;
import com.phonepe.drove.models.info.resources.allocation.MemoryAllocation;
import com.phonepe.drove.models.info.resources.allocation.ResourceAllocation;
import com.phonepe.drove.models.instance.InstanceInfo;
import com.phonepe.drove.models.instance.InstanceState;
import com.phonepe.drove.models.interfaces.DeployedInstanceInfo;
import com.phonepe.drove.models.interfaces.DeployedInstanceInfoVisitor;
import com.phonepe.drove.models.interfaces.DeploymentSpec;
import com.phonepe.drove.models.interfaces.DeploymentSpecVisitor;
import com.phonepe.drove.models.task.TaskSpec;
import com.phonepe.drove.models.taskinstance.TaskInfo;
import com.phonepe.drove.models.taskinstance.TaskState;
import io.appform.functionmetrics.MonitoredFunction;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;

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
    //Map of sessionId -> [executorId -> [ instanceId -> resources]]]
    private final Map<String, Map<String, Map<String, InstanceResourceAllocation>>> schedulingSessionData =
            new ConcurrentHashMap<>();

    @Inject
    public DefaultInstanceScheduler(
            ApplicationInstanceInfoDB instanceInfoDB,
            TaskDB taskDB,
            LocalServiceStateDB localServiceStateDB,
            ClusterResourcesDB clusterResourcesDB) {
        this.instanceInfoDB = instanceInfoDB;
        this.taskDB = taskDB;
        this.localServiceStateDB = localServiceStateDB;
        this.clusterResourcesDB = clusterResourcesDB;
    }

    @Override
    public synchronized Optional<AllocatedExecutorNode> schedule(
            String schedulingSessionId, String instanceId, DeploymentSpec deploymentSpec) {

        var placementPolicy = Objects.requireNonNullElse(deploymentSpec.getPlacementPolicy(),
                                                         new AnyPlacementPolicy());
        if (hasTagPolicy(placementPolicy)) {
            log.info("Placement policy seems to have tags already, skipping mutation");
        }
        else {
            log.info("No tags specified in placement policy, will ensure deployments don't go to tagged executors");
            placementPolicy = new CompositePlacementPolicy(List.of(placementPolicy, new NoTagPlacementPolicy()),
                                                           CompositePlacementPolicy.CombinerType.AND);
        }
        return schedule(schedulingSessionId, instanceId, deploymentSpec, placementPolicy);
    }

    @Override
    @MonitoredFunction
    public synchronized Optional<AllocatedExecutorNode> schedule(
            String schedulingSessionId, String instanceId,
            DeploymentSpec deploymentSpec,
            final PlacementPolicy placementPolicy,
            final Set<ExecutorState> allowedStates) {


        val sessionData = schedulingSessionData.computeIfAbsent(
                schedulingSessionId,
                id -> clusterSnapshot(deploymentSpec));
        val selectedNode = clusterResourcesDB.selectNodes(
                deploymentSpec.getResources(),
                allowedStates,
                allocatedNode -> validateNode(placementPolicy, sessionData, allocatedNode));
        //If a node is found, add it to the list of allocated nodes for this session
        //Next time a request for this session comes, this will ensure that allocations done in current session
        //Are taken into consideration
        selectedNode.ifPresent(allocatedExecutorNode -> schedulingSessionData.computeIfPresent(
                schedulingSessionId,
                (sid, executorAllocations) -> {
                    val executorId = allocatedExecutorNode.getExecutorId();
                    val currentAllocations = executorAllocations.computeIfAbsent(executorId, eId -> new HashMap<>());
                    currentAllocations.put(instanceId,
                                           new InstanceResourceAllocation(executorId,
                                                                          instanceId,
                                                                          allocatedExecutorNode.getCpu(),
                                                                          allocatedExecutorNode.getMemory()));
                    log.info("POST_ALLOC::SID: {} exec id: {} count: {}",
                             sid, executorId, currentAllocations.size());
                    return executorAllocations;
                }));
        return selectedNode;
    }

    @Override
    @MonitoredFunction
    public synchronized void finaliseSession(final String schedulingSessionId) {
        schedulingSessionData.remove(schedulingSessionId);
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
                                                                    removed.getMemory());
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
                                                            ResourceType.MEMORY));
            }

            @Override
            public InstanceResourceAllocation visit(TaskInfo taskInfo) {
                return new InstanceResourceAllocation(
                        taskInfo.getExecutorId(),
                        taskInfo.getInstanceId(),
                        (CPUAllocation) filterAllocation(taskInfo.getResources(), ResourceType.CPU),
                        (MemoryAllocation) filterAllocation(taskInfo.getResources(), ResourceType.MEMORY));
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
            final PlacementPolicy placementPolicy,
            Map<String, Map<String, InstanceResourceAllocation>> sessionLevelData,
            final AllocatedExecutorNode executorNode) {
        val allocatedExecutorId = executorNode.getExecutorId();

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
                return Objects.requireNonNullElse(executorNode.getTags(), Set.of()).contains(matchTag.getTag());
            }

            @Override
            public Boolean visit(NoTagPlacementPolicy noTag) {
                //Return false if node has any tag other than hostname
                return Sets.difference(Objects.requireNonNullElse(executorNode.getTags(), Set.of()),
                                       Set.of(executorNode.getHostname())).isEmpty();
            }

            @Override
            public Boolean visit(RuleBasedPlacementPolicy ruleBased) {
                //TODO::IMPLEMENT
                return false;
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

    private boolean hasTagPolicy(final PlacementPolicy policy) {
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
                        .anyMatch(DefaultInstanceScheduler.this::hasTagPolicy);
            }

            @Override
            public Boolean visit(LocalPlacementPolicy localPlacementPolicy) {
                return false;
            }
        });
    }
}
