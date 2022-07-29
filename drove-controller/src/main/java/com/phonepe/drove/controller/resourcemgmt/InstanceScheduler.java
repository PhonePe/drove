package com.phonepe.drove.controller.resourcemgmt;

import com.phonepe.drove.models.interfaces.DeploymentSpec;

import java.util.Optional;

/**
 *
 */
public interface InstanceScheduler {
    Optional<AllocatedExecutorNode> schedule(
            String schedulingSessionId,
            final DeploymentSpec applicationSpec);

    void finaliseSession(String schedulingSessionId);

    boolean discardAllocation(String schedulingSessionId, final AllocatedExecutorNode node);
}
