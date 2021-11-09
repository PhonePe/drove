package com.phonepe.drove.controller.resources;

import com.phonepe.drove.models.application.ApplicationSpec;

import java.util.Optional;

/**
 *
 */
public interface InstanceScheduler {
    Optional<AllocatedExecutorNode> schedule(final ApplicationSpec applicationSpec);
    boolean deallocate(final AllocatedExecutorNode node);
    boolean accept(final String executorInfo);
}
