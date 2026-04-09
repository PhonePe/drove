package com.phonepe.drove.controller.resourcemgmt.selector.scored;

import com.phonepe.drove.controller.resourcemgmt.AllocatedExecutorNode;

public class MaxGpuNumaLocalAllocation implements AllocatedExecutorNodeScorer {
    @Override
    public AllocationScore getScore(AllocatedExecutorNode node) {
        return new AllocationScore(node.getGpu().getLocalGpuCount(), node.getGpu().getLocalGpuCount() == node.getGpu().getGpuIds().size());
    }
}
