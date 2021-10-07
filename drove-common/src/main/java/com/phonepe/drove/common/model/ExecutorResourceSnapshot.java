package com.phonepe.drove.common.model;

import com.phonepe.drove.common.model.resources.available.AvailableCPU;
import com.phonepe.drove.common.model.resources.available.AvailableMemory;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

/**
 *
 */
@Value
@Jacksonized
@Builder
@AllArgsConstructor
public class ExecutorResourceSnapshot {
    String executorId;
    AvailableCPU cpus;
    AvailableMemory memory;
}
