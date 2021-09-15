package com.phonepe.drove.controller.scheduling;

import com.phonepe.drove.common.model.InstanceSpec;
import com.phonepe.drove.models.application.ApplicationSpec;

import java.util.List;

/**
 *
 */
public interface InstanceScheduler {
    List<InstanceSpec> schedule(final ApplicationSpec applicationSpec);
}
