package com.phonepe.drove.controller.resourcemgmt;

import com.phonepe.drove.models.application.ApplicationSpec;

import java.util.Optional;

/**
 *
 */
public interface InstanceScheduler {
    Optional<AllocatedExecutorNode> schedule(
            String schedulingSessionId,
            final ApplicationSpec applicationSpec);

    void finaliseSession(String schedulingSessionId);

    boolean discardAllocation(String schedulingSessionId, final AllocatedExecutorNode node);
}
