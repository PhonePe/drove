package com.phonepe.drove.controller.resourcemgmt.selector.scored;

import com.phonepe.drove.controller.resourcemgmt.allocator.ExecutorAllocation;

/**
 * Scores an executor allocation to enable comparison-based selection.
 * Implementations define the scoring strategy (e.g. NUMA-local GPU count).
 */
public interface AllocatedExecutorNodeScorer {

    /**
     * Computes a score for the given allocation.
     *
     * @param allocation the executor allocation to score
     * @return an {@link AllocationScore} containing the numeric score and whether it is the maximum possible
     */
    AllocationScore getScore(ExecutorAllocation allocation);

    /**
     * Represents the result of scoring an allocation.
     *
     * @param score numeric score; higher is better
     * @param isMax {@code true} if this score is the maximum achievable, allowing early termination of search
     */
    record AllocationScore(long score, boolean isMax) {
    }
}
