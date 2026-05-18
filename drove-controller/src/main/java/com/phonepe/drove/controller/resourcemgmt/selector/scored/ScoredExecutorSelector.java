package com.phonepe.drove.controller.resourcemgmt.selector.scored;

import com.phonepe.drove.controller.resourcemgmt.AllocatedExecutorNode;
import com.phonepe.drove.controller.resourcemgmt.ExecutorHostInfo;
import com.phonepe.drove.controller.resourcemgmt.allocator.ExecutorResourceAllocator;
import com.phonepe.drove.controller.resourcemgmt.selector.ExecutorSelector;
import com.phonepe.drove.controller.utils.ControllerUtils;
import com.phonepe.drove.models.application.requirements.GpuType;
import com.phonepe.drove.models.application.requirements.ResourceRequirement;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import lombok.val;

import javax.inject.Inject;

/**
 * Chooses the feasible executor whose allocation has the most NUMA-local GPUs.
 * The localGpuCount is pre-calculated by the ResourceAllocator and stored in GPUAllocation.
 * This selector simply scores executors based on that pre-calculated value.
 * Ties retain the incoming node order. Short-circuits if max score is found.
 * Applies both nodeFilter and allocationFilter inline during a single pass.
 */
public class ScoredExecutorSelector implements ExecutorSelector {

    private final ExecutorResourceAllocator allocator;
    private final AllocatedExecutorNodeScorer scorer;

    @Inject
    public ScoredExecutorSelector(ExecutorResourceAllocator allocator, AllocatedExecutorNodeScorer scorer) {
        this.allocator = allocator;
        this.scorer = scorer;
    }

    @Override
    public Optional<AllocatedExecutorNode> select(
            List<ExecutorHostInfo> nodes,
            Predicate<ExecutorHostInfo> nodeFilter,
            List<ResourceRequirement> requirements,
            Predicate<AllocatedExecutorNode> allocationFilter) {
        
        val cpus = ControllerUtils.extractCpuRequirement(requirements);
        val memory = ControllerUtils.extractMemoryRequirement(requirements);
        val gpuReq = ControllerUtils.findGpuRequirement(requirements).orElse(null);
        val gpus = gpuReq != null ? gpuReq.getCount() : 0;
        val gpuType = gpuReq != null ? gpuReq.getGpuType() : null;
        
        Optional<AllocatedExecutorNode> selected = Optional.empty();
        long maxScore = -1;
        
        for (ExecutorHostInfo node : nodes) {
            if (nodeFilter.test(node)) {
                val candidate = tryAllocate(node, cpus, memory, gpus, gpuType, allocationFilter);
                if (candidate.isPresent() && candidate.get().score() > maxScore) {
                    maxScore = candidate.get().score();
                    selected = Optional.of(candidate.get().node());
                    if (candidate.get().isMax()) {
                        break;
                    }
                }
            }
        }

        return selected;
    }

    private Optional<ScoredAllocation> tryAllocate(
            ExecutorHostInfo node,
            long cpus,
            long memory,
            long gpus,
            GpuType gpuType,
            Predicate<AllocatedExecutorNode> allocationFilter) {
        val allocationResult = allocator.allocate(node, cpus, memory, gpus, gpuType);
        if (allocationResult.isEmpty()) {
            return Optional.empty();
        }
        val executorAllocation = allocationResult.get();
        val allocatedNode = executorAllocation.getAllocatedNode();
        if (!allocationFilter.test(allocatedNode)) {
            return Optional.empty();
        }
        val score = scorer.getScore(executorAllocation);
        return Optional.of(new ScoredAllocation(allocatedNode, score.score(), score.isMax()));
    }

    private record ScoredAllocation(AllocatedExecutorNode node, long score, boolean isMax) {
    }
}

