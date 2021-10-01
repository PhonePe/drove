package com.phonepe.drove.controller.resources;

import com.phonepe.drove.common.model.resources.allocation.CPUAllocation;
import com.phonepe.drove.common.model.resources.allocation.MemoryAllocation;
import lombok.Value;

/**
 *
 */
@Value
public class ExecutorNode {
    String executorId;
    String hostname;
    int port;
    CPUAllocation cpu;
    MemoryAllocation memory;
}
