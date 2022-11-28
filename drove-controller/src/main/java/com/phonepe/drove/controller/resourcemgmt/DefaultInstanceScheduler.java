package com.phonepe.drove.controller.resourcemgmt;

import com.google.common.collect.Sets;
import com.phonepe.drove.controller.statedb.ApplicationInstanceInfoDB;
import com.phonepe.drove.controller.statedb.TaskDB;
import com.phonepe.drove.controller.utils.ControllerUtils;
import com.phonepe.drove.models.application.ApplicationSpec;
import com.phonepe.drove.models.application.placement.PlacementPolicy;
import com.phonepe.drove.models.application.placement.PlacementPolicyVisitor;
import com.phonepe.drove.models.application.placement.policies.*;
import com.phonepe.drove.models.instance.InstanceInfo;
import com.phonepe.drove.models.instance.InstanceState;
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
    private final ApplicationInstanceInfoDB instanceInfoDB;
    private final TaskDB taskDB;
    private final ClusterResourcesDB clusterResourcesDB;
    private final Map<String, Map<String, Long>> schedulingSessionData = new ConcurrentHashMap<>();

    @Inject
    public DefaultInstanceScheduler(
            ApplicationInstanceInfoDB instanceInfoDB, TaskDB taskDB, ClusterResourcesDB clusterResourcesDB) {
        this.instanceInfoDB = instanceInfoDB;
        this.taskDB = taskDB;
        this.clusterResourcesDB = clusterResourcesDB;
    }

    @Override
    @MonitoredFunction
    public synchronized Optional<AllocatedExecutorNode> schedule(
            String schedulingSessionId, DeploymentSpec applicationSpec) {
        //Take a snapshot of all instances in this cluster at the onset of the session
        //This will get augmented every time a new node is allocated in this session
        schedulingSessionData.computeIfAbsent(schedulingSessionId,
                                              id -> new HashMap<>(clusterSnapshot(applicationSpec)));
        var placementPolicy = Objects.requireNonNullElse(applicationSpec.getPlacementPolicy(),
                                                         new AnyPlacementPolicy());
        if(hasTagPolicy(placementPolicy)) {
            log.info("Placement policy seems to have tags already, skipping mutation");
        }
        else {
            log.info("No tags specified in placement policy, will ensure deployments don't go to tagged executors");
            placementPolicy = new CompositePlacementPolicy(List.of(placementPolicy, new NoTagPlacementPolicy()),
                                                           CompositePlacementPolicy.CombinerType.AND);
        }

        val finalPlacementPolicy = placementPolicy;
        val selectedNode = clusterResourcesDB.selectNodes(applicationSpec.getResources(),
                                                          allocatedNode -> validateNode(
                                                                  finalPlacementPolicy,
                                                                  schedulingSessionData.get(
                                                                          schedulingSessionId),
                                                                  allocatedNode));
        //If a node is found, add it to the list of allocated nodes for this session
        //Next time a request for this session comes, this will ensure that allocations done in current session
        //Are taken into consideration
        selectedNode.ifPresent(allocatedExecutorNode -> schedulingSessionData.computeIfPresent(
                schedulingSessionId,
                (sid, executors) -> {
                    executors.compute(allocatedExecutorNode.getExecutorId(),
                                      (eid, existingCount) -> null == existingCount
                                                              ? 1L
                                                              : existingCount + 1L);
                    return executors;
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
    public synchronized boolean discardAllocation(String schedulingSessionId, AllocatedExecutorNode node) {
        clusterResourcesDB.deselectNode(node);
        schedulingSessionData.computeIfPresent(schedulingSessionId, (id, executors) -> {
            executors.remove(node.getExecutorId());
            return executors;
        });
        return true;
    }

    private Map<String, Long> clusterSnapshot(DeploymentSpec deploymentSpec) {
        return deploymentSpec.accept(new DeploymentSpecVisitor<Map<String, Long>>() {
            @Override
            public Map<String, Long> visit(ApplicationSpec applicationSpec) {
                return instanceInfoDB.instances(Set.of(ControllerUtils.deployableObjectId(applicationSpec)),
                                                RESOURCE_CONSUMING_INSTANCE_STATES,
                                                false)
                        .values()
                        .stream()
                        .flatMap(Collection::stream)
                        .collect(Collectors.groupingBy(InstanceInfo::getExecutorId, Collectors.counting()));
            }

            @Override
            public Map<String, Long> visit(TaskSpec taskSpec) {
                return taskDB.tasks(Set.of(taskSpec.getSourceAppName()), RESOURCE_CONSUMING_TASK_STATES, false)
                        .values()
                        .stream()
                        .flatMap(Collection::stream)
                        .collect(Collectors.groupingBy(TaskInfo::getExecutorId, Collectors.counting()));
            }
        });
    }

    private boolean validateNode(
            final PlacementPolicy placementPolicy,
            Map<String, Long> sessionLevelData,
            final AllocatedExecutorNode executorNode) {
        val allocatedExecutorId = executorNode.getExecutorId();
        return placementPolicy.accept(new PlacementPolicyVisitor<>() {
            @Override
            public Boolean visit(OnePerHostPlacementPolicy onePerHost) {
                log.debug("Existing: {} allocated: {}", sessionLevelData.keySet(), allocatedExecutorId);
                return !sessionLevelData.containsKey(allocatedExecutorId);
            }

            @Override
            public Boolean visit(MaxNPerHostPlacementPolicy maxNPerHost) {
                val numExistingInstances = sessionLevelData.getOrDefault(allocatedExecutorId, 0L);
                log.debug("Existing Instances: {} on allocated executor: {}",
                          numExistingInstances,
                          allocatedExecutorId);
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
        });
    }
}
