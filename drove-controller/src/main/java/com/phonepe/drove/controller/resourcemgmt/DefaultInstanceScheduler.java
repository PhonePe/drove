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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
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
import com.phonepe.drove.controller.rule.PlacementRuleEvaluator;
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
import com.phonepe.drove.models.info.nodedata.ExecutorNodeData;
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
    private static final int MAX_REPORTED_EXECUTORS = 10;

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
    private final PlacementRuleEvaluator ruleEvaluator;
    //Map of sessionId -> [executorId -> [ instanceId -> resources]]]
    private final Map<String, Map<String, Map<String, InstanceResourceAllocation>>> schedulingSessionData =
            new ConcurrentHashMap<>();

    @Inject
    public DefaultInstanceScheduler(
            ApplicationInstanceInfoDB instanceInfoDB,
            TaskDB taskDB,
            LocalServiceStateDB localServiceStateDB,
            ClusterResourcesDB clusterResourcesDB,
            PlacementRuleEvaluator ruleEvaluator) {
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
        return place(clusterResourcesDB,
                     schedulingSessionId,
                     List.of(instanceId),
                     deploymentSpec,
                     placementPolicy,
                     allowedStates,
                     schedulingSessionData,
                     true)
            .stream()
            .findAny();
    }

    /**
     * Simulates the placement on a deep copy of the cluster resources DB. The
     * simulation does not change the real DB. The result is only a hint. The
     * cluster state can change before the real scheduling starts.
     */
    @Override
    @MonitoredFunction
    public PlacementCheckResult checkPlacement(final DeploymentSpec deploymentSpec,
                                               final PlacementPolicy requiredPlacementPolicy,
                                               final int numInstances,
                                               final boolean detail) {
        val placementPolicy = effectivePolicy(deploymentSpec, requiredPlacementPolicy);

        val schedulingSessionId = UUID.randomUUID().toString();
        //Read the existing allocations directly before the copy, so that the two data sets are close in time
        val localSessionData = new ConcurrentHashMap<>(Map.of(schedulingSessionId, clusterSnapshot(deploymentSpec)));
        val simulationDB = clusterResourcesDB.copy();
        val instanceIds = IntStream.rangeClosed(1, numInstances)
                .mapToObj(i -> "%s-simulation-instance-%d".formatted(deploymentSpec.getType().name().toLowerCase(), i))
                .toList();
        //Count needs the full placement run; expose placements only when detail is set
        val placements = place(simulationDB,
                               schedulingSessionId,
                               instanceIds,
                               deploymentSpec,
                               placementPolicy,
                               EnumSet.of(ExecutorState.ACTIVE),
                               localSessionData,
                               false);
        return PlacementCheckResult.builder()
                .requested(numInstances)
                .placed(placements.size())
                .errors(placements.size() == numInstances
                        ? List.of()
                        : placementErrors(simulationDB, deploymentSpec, numInstances, placements.size()))
                .placements(detail ? placements : List.of())
                .build();
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

    private static InstanceResourceAllocation allocation(
            final String executorId,
            final String instanceId,
            final AllocatedExecutorNode allocatedExecutorNode) {
        return new InstanceResourceAllocation(executorId,
                                              instanceId,
                                              allocatedExecutorNode.getCpu(),
                                              allocatedExecutorNode.getMemory(),
                                              allocatedExecutorNode.getGpu());
    }

    private List<AllocatedExecutorNode> place(
            final ClusterResourcesDB resourcesDB,
            @NonNull final String schedulingSessionId,
            final List<String> instanceIds,
            final DeploymentSpec deploymentSpec,
            final PlacementPolicy placementPolicy,
            final Set<ExecutorState> allowedStates,
            final Map<String, Map<String, Map<String, InstanceResourceAllocation>>> providedSessionData,
            final boolean shouldShuffle) {
        val sessionData = providedSessionData.computeIfAbsent(
                schedulingSessionId,
                id -> {
                    log.info("CREATE::SID: {}", schedulingSessionId);
                    return clusterSnapshot(deploymentSpec);
                });
        return resourcesDB.selectNodes(
                deploymentSpec.getResources(),
                allowedStates,
                filterContext -> validateNode(filterContext,
                                              schedulingSessionId,
                                              filterContext.getInstanceId(),
                                              placementPolicy,
                                              sessionData,
                                              deploymentSpec),
                shouldShuffle,
                instanceIds,
                (instanceId, allocatedExecutorNode) -> recordAllocation(providedSessionData,
                                                                        schedulingSessionId,
                                                                        instanceId,
                                                                        allocatedExecutorNode));
    }

    private PlacementPolicy effectivePolicy(final DeploymentSpec deploymentSpec,
                                            final PlacementPolicy override) {
        if (null == override) {
            return ControllerUtils.computeEffectivePlacementPolicy(deploymentSpec);
        }
        if (deploymentSpec instanceof LocalServiceSpec
                || ControllerUtils.shouldAllowCombiningOfPolicy(override)) {
            return override;
        }
        return new CompositePlacementPolicy(List.of(override, new NoTagPlacementPolicy()),
                                            CompositePlacementPolicy.CombinerType.AND);

    }

    private void recordAllocation(final Map<String, Map<String, Map<String, InstanceResourceAllocation>>> providedSessionData,
                                  final String schedulingSessionId,
                                  final String instanceId,
                                  final AllocatedExecutorNode allocatedExecutorNode) {
        //If a node is found, add it to the list of allocated nodes for this session
        //Next time a request for this session comes, this will ensure that allocations done in current session
        //Are taken into consideration
        providedSessionData.computeIfPresent(
            schedulingSessionId,
            (sid, executorAllocations) -> {
                val executorId = allocatedExecutorNode.getExecutorId();
                val currentAllocations = executorAllocations.computeIfAbsent(executorId,
                                                                             eId -> new HashMap<>());
                currentAllocations.put(instanceId, allocation(executorId, instanceId, allocatedExecutorNode));
                log.debug("POST_ALLOC::SID: {} exec id: {} count: {}", sid, executorId, currentAllocations.size());
                return executorAllocations;
            });
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

    private List<String> placementErrors(final ClusterResourcesDB resourcesDB,
                                         final DeploymentSpec deploymentSpec,
                                         final int numInstances,
                                         final int placed) {
        val errors = new ArrayList<String>();
        val appName = ControllerUtils.deployableObjectId(deploymentSpec);
        errors.add("Cluster can place only " + placed + " of " + numInstances
                + " requested instances for " + appName);
        val requirements = deploymentSpec.getResources();
        val requiredCores = ControllerUtils.extractCpuRequirement(requirements);
        val requiredMem = ControllerUtils.extractMemoryRequirement(requirements);
        errors.add("Required per instance: " + requiredCores + " cores, " + requiredMem + " MB memory");
        val snapshot = resourcesDB.currentSnapshot(true);
        val maxFreeCoresPerNumaNode = snapshot.stream()
                .map(ExecutorHostInfo::getNodes)
                .filter(Objects::nonNull)
                .flatMap(numaNodes -> numaNodes.values().stream())
                .mapToInt(numaNode -> (int) numaNode.getCores()
                        .values()
                        .stream()
                        .filter(ExecutorHostInfo.CoreState.FREE::equals)
                        .count())
                .max()
                .orElse(Integer.MAX_VALUE);
        if (maxFreeCoresPerNumaNode < requiredCores) {
            errors.add("Required cores exceed the maximum free cores on a single "
                    + "NUMA node in the cluster. Required: " + requiredCores
                    + " Max free: " + maxFreeCoresPerNumaNode);
        }
        ControllerUtils.findGpuRequirement(requirements).ifPresent(gpuRequirement -> {
            val gpuType = gpuRequirement.getGpuType();
            val requiredGpusPerInstance = gpuRequirement.getCount();
            val totalFreeGpus = snapshot.stream()
                    .mapToInt(executor -> ControllerUtils.freeGpus(executor, gpuType))
                    .sum();
            val maxFreeGpusPerExecutor = snapshot.stream()
                    .mapToInt(executor -> ControllerUtils.freeGpus(executor, gpuType))
                    .max()
                    .orElse(Integer.MAX_VALUE);


            if (maxFreeGpusPerExecutor < requiredGpusPerInstance) {
                errors.add("Required GPU count exceeds the maximum GPU count available on a single "
                        + "executor in the cluster. Required: " + requiredGpusPerInstance
                        + " Max: " + maxFreeGpusPerExecutor);
            }
            if (totalFreeGpus < requiredGpusPerInstance * numInstances) {
                errors.add("Cluster does not have enough GPUs of required type " + gpuType
                        + ". Required: " + (requiredGpusPerInstance * numInstances)
                        + " Available: " + totalFreeGpus);
            }
        });
        val executorCapacities = snapshot
                .stream()
                .sorted(Comparator.comparingLong(ControllerUtils::freeCores).reversed())
                .map(executor -> executor.getExecutorId()
                        + "[cores: " + ControllerUtils.freeCores(executor)
                        + ", memory: " + ControllerUtils.freeMemory(executor) + " MB]")
                .collect(Collectors.toList());
        errors.add("Per executor free capacity: "
                + String.join(", ", executorCapacities.subList(0, Math.min(executorCapacities.size(), MAX_REPORTED_EXECUTORS)))
                + (executorCapacities.size() > MAX_REPORTED_EXECUTORS
                        ? String.format(" ... and %d more executors",
                                        executorCapacities.size() - MAX_REPORTED_EXECUTORS)
                        : ""));


        return errors;
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
                val allocatedNode = filterContext.getAllocatedExecutorNodeData();
                return Sets.difference(Objects.requireNonNullElse(allocatedNode.getTags(), Set.of()),
                                       Set.of(allocatedNode.getHostname())).isEmpty();
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
                val combiner = Objects.requireNonNullElse(compositePlacementPolicy.getCombiner(),
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
