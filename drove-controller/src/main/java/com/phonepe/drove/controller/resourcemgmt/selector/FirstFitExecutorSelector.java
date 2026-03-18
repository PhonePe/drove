package com.phonepe.drove.controller.resourcemgmt.selector;

import com.phonepe.drove.controller.resourcemgmt.AllocatedExecutorNode;
import com.phonepe.drove.controller.resourcemgmt.ExecutorHostInfo;
import com.phonepe.drove.controller.resourcemgmt.allocator.ExecutorResourceAllocator;
import com.phonepe.drove.models.application.requirements.ResourceRequirement;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * Preserves current first-fit executor selection semantics.
 * Applies both nodeFilter and allocationFilter inline during a single pass.
 */
public class FirstFitExecutorSelector implements ExecutorSelector {

	private final ExecutorResourceAllocator allocator;

	public FirstFitExecutorSelector(ExecutorResourceAllocator allocator) {
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
		long gpus = ExecutorSelector.extractGpuRequirement(requirements);
		
		// Single pass: apply nodeFilter, allocate, apply allocationFilter inline
		return nodes.stream()
				.filter(nodeFilter)
				.map(node -> allocator.allocate(node, cpus, memory, gpus))
				.filter(Optional::isPresent)
				.map(Optional::get)
				.filter(allocationFilter)
				.findFirst();
	}
}


