package com.phonepe.drove.controller.resourcemgmt.selector.scored;

import com.phonepe.drove.controller.resourcemgmt.AllocatedExecutorNode;
import com.phonepe.drove.controller.resourcemgmt.allocator.ExecutorAllocation;

public class MaxGpuNumaLocalAllocation implements AllocatedExecutorNodeScorer {
    @Override
    public AllocationScore getScore(ExecutorAllocation allocation) {
        final var node = allocation.getAllocatedNode();
        if(allocation.getAllocatedNode().getGpu() == null){
            // This would mean that gpus were not requested,
            // so we can consider this as a perfect score for this scorer since it is not applicable.
            return new AllocationScore(0, true);
        }
        return new AllocationScore(allocation.getMetadata().getNumaLocalGpuCount(), allocation.getMetadata().getNumaLocalGpuCount() == node.getGpu().getGpuIds().size());
    }
}
