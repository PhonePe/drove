package com.phonepe.drove.controller.resourcemgmt.allocator;

import com.phonepe.drove.controller.resourcemgmt.AllocatedExecutorNode;
import lombok.Value;

@Value
public class ExecutorAllocation {
    AllocatedExecutorNode allocatedNode;
    AllocationMetadata metadata;
}
