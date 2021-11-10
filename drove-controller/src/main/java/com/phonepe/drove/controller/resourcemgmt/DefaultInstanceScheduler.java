package com.phonepe.drove.controller.resourcemgmt;

import com.phonepe.drove.controller.statedb.ApplicationStateDB;
import com.phonepe.drove.models.application.ApplicationSpec;
import com.phonepe.drove.models.application.placement.PlacementPolicyVisitor;
import com.phonepe.drove.models.application.placement.policies.*;
import com.phonepe.drove.models.instance.InstanceInfo;
import lombok.val;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 *
 */
@Singleton
public class DefaultInstanceScheduler implements InstanceScheduler {
    private final ApplicationStateDB applicationStateDB;
    private final ClusterResourcesDB clusterResourcesDB;

    @Inject
    public DefaultInstanceScheduler(
            ApplicationStateDB applicationStateDB,
            ClusterResourcesDB clusterResourcesDB) {
        this.applicationStateDB = applicationStateDB;
        this.clusterResourcesDB = clusterResourcesDB;
    }

    @Override
    public Optional<AllocatedExecutorNode> schedule(ApplicationSpec applicationSpec) {
        return clusterResourcesDB.selectNodes(applicationSpec.getResources(),
                                                  1,
                                                  allocatedNode -> validateNode(applicationSpec, allocatedNode))
                .stream()
                .findFirst();
    }

    @Override
    public boolean deallocate(AllocatedExecutorNode node) {
        clusterResourcesDB.deselectNode(node);
        return true;
    }

    @Override
    public boolean accept(String executorInfo) {
        return false;
    }

    boolean validateNode(final ApplicationSpec spec, final AllocatedExecutorNode executorNode) {
        val existing = applicationStateDB.instances(spec.getName(), 0, Integer.MAX_VALUE)
                .stream()
                .collect(Collectors.groupingBy(InstanceInfo::getExecutorId, Collectors.counting()));
        val allocatedExecutorId = executorNode.getExecutorId();
        return spec.getPlacementPolicy().accept(new PlacementPolicyVisitor<>() {
            @Override
            public Boolean visit(OnePerHostPlacementPolicy onePerHost) {
                return !existing.containsKey(allocatedExecutorId);
            }

            @Override
            public Boolean visit(MaxNPerHostPlacementPolicy maxNPerHost) {
                return existing.getOrDefault(allocatedExecutorId, 0L) <= maxNPerHost.getNumContainers();
            }

            @Override
            public Boolean visit(MatchTagPlacementPolicy matchTag) {
                //TODO::IMPLEMENT
                return false;
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
