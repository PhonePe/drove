package com.phonepe.drove.common.model;

import com.phonepe.drove.common.model.resources.available.AvailableCPU;
import com.phonepe.drove.common.model.resources.available.AvailableMemory;
import lombok.Value;

/**
 *
 */
@Value
public class ExecutorState {
    String executorId;
    AvailableCPU cpus;
    AvailableMemory memory;
}
