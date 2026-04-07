package com.phonepe.drove.controller.resourcemgmt.selector;

import com.phonepe.drove.controller.resourcemgmt.AllocatedExecutorNode;
import com.phonepe.drove.controller.resourcemgmt.ExecutorHostInfo;
import com.phonepe.drove.controller.resourcemgmt.allocator.ExecutorResourceAllocator;
import com.phonepe.drove.models.application.requirements.GpuType;
import com.phonepe.drove.models.application.requirements.ResourceRequirement;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import lombok.val;

/**
 * Chooses the feasible executor whose allocation has the most NUMA-local GPUs.
 * The localGpuCount is pre-calculated by the ResourceAllocator and stored in GPUAllocation.
 * This selector simply scores executors based on that pre-calculated value.
 * Ties retain the incoming node order. Short-circuits if max score is found.
 * 
 * Applies both nodeFilter and allocationFilter inline during a single pass.
 */
public class ScoredExecutorSelector implements ExecutorSelector {

    private final ExecutorResourceAllocator allocator;

    public ScoredExecutorSelector(ExecutorResourceAllocator allocator) {
        this.allocator = allocator;
    }

    @Override
    public Optional<AllocatedExecutorNode> select(
            List<ExecutorHostInfo> nodes,
            Predicate<ExecutorHostInfo> nodeFilter,
            List<ResourceRequirement> requirements,
            Predicate<AllocatedExecutorNode> allocationFilter) {
        
        long cpus = ExecutorSelector.extractCpuRequirement(requirements);
        long memory = ExecutorSelector.extractMemoryRequirement(requirements);
        val gpuReq = ExecutorSelector.findGpuRequirement(requirements).orElse(null);
        long gpus = gpuReq != null ? gpuReq.getCount() : 0;
        GpuType gpuType = gpuReq != null ? gpuReq.getGpuType() : null;
        
        Optional<AllocatedExecutorNode> selected = Optional.empty();
        long maxScore = -1;
        
        // Single pass: apply nodeFilter, allocate, apply allocationFilter, score inline
        for (ExecutorHostInfo node : nodes) {
            if (!nodeFilter.test(node)) {
                continue;
            }
            
            Optional<AllocatedExecutorNode> allocation = allocator.allocate(node, cpus, memory, gpus, gpuType);
            
            if (allocation.isPresent() && allocationFilter.test(allocation.get())) {
                // Use the pre-calculated localGpuCount from the allocation
                long score = allocation.get().getGpu().getLocalGpuCount();
                if (score > maxScore) {
                    maxScore = score;
                    selected = allocation;
                    // Short-circuit: if we found allocation with max possible score, return immediately
                    if (score == allocation.get().getGpu().getGpuIds().size()) {
                        return selected;
                    }
                }
            }
        }
        
        return selected;
    }
}

