package com.phonepe.drove.controller.resourcemgmt.selector.scored;

import com.phonepe.drove.controller.resourcemgmt.AllocatedExecutorNode;
import com.phonepe.drove.controller.resourcemgmt.allocator.ExecutorAllocation;

public class MaxGpuNumaLocalAllocation implements AllocatedExecutorNodeScorer {
    @Override
    public AllocationScore getScore(ExecutorAllocation allocation) {
        final var node = allocation.getAllocatedNode();
        return new AllocationScore(allocation.getMetadata().getNumaLocalGpuCount(), allocation.getMetadata().getNumaLocalGpuCount() == node.getGpu().getGpuIds().size());
    }
}
