package com.phonepe.drove.controller.resourcemgmt.selector.scored;

import com.phonepe.drove.controller.resourcemgmt.allocator.ExecutorAllocation;

public interface AllocatedExecutorNodeScorer {
    AllocationScore getScore(ExecutorAllocation node);

    record AllocationScore(long score, boolean isMax) {
    }
}
