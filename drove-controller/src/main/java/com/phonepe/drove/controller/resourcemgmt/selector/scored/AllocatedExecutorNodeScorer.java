package com.phonepe.drove.controller.resourcemgmt.selector.scored;

import com.phonepe.drove.controller.resourcemgmt.AllocatedExecutorNode;

public interface AllocatedExecutorNodeScorer {
    AllocationScore getScore(AllocatedExecutorNode node);

    record AllocationScore(long score, boolean isMax) {
    }
}
