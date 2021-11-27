package com.phonepe.drove.controller.resourcemgmt;

import com.phonepe.drove.models.info.resources.allocation.CPUAllocation;
import com.phonepe.drove.models.info.resources.allocation.MemoryAllocation;
import lombok.Value;

import java.util.Set;

/**
 *
 */
@Value
public class AllocatedExecutorNode {
    String executorId;
    String hostname;
    int port;
    CPUAllocation cpu;
    MemoryAllocation memory;
    Set<String> tags;
}
