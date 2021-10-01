package com.phonepe.drove.controller.resources;

import com.phonepe.drove.controller.statedb.ApplicationStateDB;
import com.phonepe.drove.models.application.ApplicationSpec;
import com.phonepe.drove.models.application.placement.PlacementPolicyVisitor;
import com.phonepe.drove.models.application.placement.policies.*;

import java.util.List;

/**
 *
 */
public class DefaultInstanceScheduler implements InstanceScheduler {
    private final ApplicationStateDB applicationStateDB;

    public DefaultInstanceScheduler(ApplicationStateDB applicationStateDB) {
        this.applicationStateDB = applicationStateDB;
    }

    @Override
    public void schedule(ApplicationSpec applicationSpec) {
        applicationSpec.getPlacementPolicy().accept(new PlacementPolicyVisitor<List<String>>() {
            @Override
            public List<String> visit(OnePerHostPlacementPolicy onePerHost) {
                return null;
            }

            @Override
            public List<String> visit(MaxNPerHostPlacementPolicy maxNPerHost) {
                return null;
            }

            @Override
            public List<String> visit(MatchTagPlacementPolicy matchTag) {
                return null;
            }

            @Override
            public List<String> visit(RuleBasedPlacementPolicy ruleBased) {
                return null;
            }

            @Override
            public List<String> visit(AnyPlacementPolicy anyPlacementPolicy) {
                return null;
            }
        });
    }

    @Override
    public boolean accept(String executorInfo) {
        return false;
    }
}
