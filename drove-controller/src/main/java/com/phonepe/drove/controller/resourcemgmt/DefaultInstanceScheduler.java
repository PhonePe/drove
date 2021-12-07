package com.phonepe.drove.controller.resourcemgmt;

import com.phonepe.drove.controller.statedb.ApplicationStateDB;
import com.phonepe.drove.controller.utils.ControllerUtils;
import com.phonepe.drove.models.application.ApplicationSpec;
import com.phonepe.drove.models.application.placement.PlacementPolicyVisitor;
import com.phonepe.drove.models.application.placement.policies.*;
import com.phonepe.drove.models.instance.InstanceInfo;
import com.phonepe.drove.models.instance.InstanceState;
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
    private final ApplicationStateDB applicationStateDB;
    private final ClusterResourcesDB clusterResourcesDB;
    private final Map<String, Map<String, Long>> schedulingSessionData = new ConcurrentHashMap<>();

    @Inject
    public DefaultInstanceScheduler(
            ApplicationStateDB applicationStateDB,
            ClusterResourcesDB clusterResourcesDB) {
        this.applicationStateDB = applicationStateDB;
        this.clusterResourcesDB = clusterResourcesDB;
    }

    @Override
    @MonitoredFunction
    public synchronized Optional<AllocatedExecutorNode> schedule(
            String schedulingSessionId, ApplicationSpec applicationSpec) {
        //Take a snapshot of all instances in this cluster at the onset of the session
        //This will get augmented every time a new node is allocated in this session
        schedulingSessionData.computeIfAbsent(schedulingSessionId, id -> new HashMap<>(clusterSnapshot(applicationSpec)));
        val selectedNode = clusterResourcesDB.selectNodes(applicationSpec.getResources(),
                                                          allocatedNode -> validateNode(applicationSpec,
                                                                                        schedulingSessionData.get(schedulingSessionId),
                                                                                        allocatedNode));
        //If a node is found, add it to the list of allocated nodes for this session
        //Next time a request for this session comes, this will ensure that allocations done in current session
        //Are taken into consideration
        selectedNode.ifPresent(allocatedExecutorNode -> schedulingSessionData.computeIfPresent(
                schedulingSessionId,
                (sid, executors) -> {
                    executors.compute(allocatedExecutorNode.getExecutorId(),
                            (eid, existingCount) -> null == existingCount ? 1L : existingCount + 1L);
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

    private Map<String, Long> clusterSnapshot(ApplicationSpec applicationSpec) {
        return applicationStateDB.activeInstances(ControllerUtils.appId(applicationSpec), 0, Integer.MAX_VALUE)
                .stream()
                .filter(instanceInfo -> RESOURCE_CONSUMING_INSTANCE_STATES.contains(instanceInfo.getState()))
                .collect(Collectors.groupingBy(InstanceInfo::getExecutorId, Collectors.counting()));
    }

    private boolean validateNode(
            final ApplicationSpec spec,
            Map<String, Long> sessionLevelData,
            final AllocatedExecutorNode executorNode) {
        val allocatedExecutorId = executorNode.getExecutorId();
        return spec.getPlacementPolicy().accept(new PlacementPolicyVisitor<>() {
            @Override
            public Boolean visit(OnePerHostPlacementPolicy onePerHost) {
                log.debug("Existing: {} allocated: {}", sessionLevelData.keySet(), allocatedExecutorId);
                return !sessionLevelData.containsKey(allocatedExecutorId);
            }

            @Override
            public Boolean visit(MaxNPerHostPlacementPolicy maxNPerHost) {
                val numExistingInstances = sessionLevelData.getOrDefault(allocatedExecutorId, 0L);
                log.debug("Existing Instances: {} on allocated executor: {}", numExistingInstances, allocatedExecutorId);
                return numExistingInstances < maxNPerHost.getMax();
            }

            @Override
            public Boolean visit(MatchTagPlacementPolicy matchTag) {
                return matchTag.isNegate() != executorNode.getTags().contains(matchTag.getTag());
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
        });
    }
}
