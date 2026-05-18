package com.phonepe.drove.controller.resourcemgmt.selector.scored;

import com.phonepe.drove.controller.resourcemgmt.allocator.CandidateExecutorAllocation;
import lombok.val;

/**
 * Scorer that favours allocations where all assigned GPUs are NUMA-local to the selected CPU node.
 * <p>
 * The score equals the number of NUMA-local GPUs in the allocation.
 * An allocation is considered max (perfect) when every assigned GPU is NUMA-local.
 * If no GPUs were requested, a max score of 0 is returned since this scorer is not applicable.
 */
public class MaxGpuNumaLocalAllocation implements AllocatedExecutorNodeScorer {
    @Override
    public AllocationScore getScore(CandidateExecutorAllocation allocation) {
        val node = allocation.getAllocatedNode();
        if(allocation.getAllocatedNode().getGpu() == null){
            // This would mean that gpus were not requested,
            // so we can consider this as a perfect score for this scorer since it is not applicable.
            return new AllocationScore(0, true);
        }
        return new AllocationScore(allocation.getMetadata().getNumaLocalGpuCount(), allocation.getMetadata().getNumaLocalGpuCount() == node.getGpu().getGpuIds().size());
    }
}
