package com.phonepe.drove.controller.resources;

import com.phonepe.drove.models.application.ApplicationSpec;

/**
 *
 */
public interface InstanceScheduler {
    void schedule(final ApplicationSpec applicationSpec);
    boolean accept(final String executorInfo);
}
