package com.phonepe.drove.controller.resourcemgmt.allocator;

import com.phonepe.drove.controller.resourcemgmt.AllocatedExecutorNode;
import lombok.Value;

/**
 * Represents a candidate allocation result from the resource allocator.
 * Pairs the actual allocated executor node with metadata about the allocation
 * that can be used for scoring and selection.
 */
@Value
public class CandidateExecutorAllocation {
    AllocatedExecutorNode allocatedNode;
    CandidateAllocationMetadata metadata;
}
