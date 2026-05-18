package com.phonepe.drove.controller.resourcemgmt.selector;

import com.phonepe.drove.controller.resourcemgmt.AllocatedExecutorNode;
import com.phonepe.drove.controller.resourcemgmt.ExecutorHostInfo;
import com.phonepe.drove.controller.resourcemgmt.allocator.CandidateExecutorAllocation;
import com.phonepe.drove.controller.resourcemgmt.allocator.ExecutorResourceAllocator;
import com.phonepe.drove.controller.utils.ControllerUtils;
import com.phonepe.drove.models.application.requirements.GpuType;
import com.phonepe.drove.models.application.requirements.ResourceRequirement;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import lombok.val;

import javax.inject.Inject;

/**
 * Selects the first executor node that satisfies the resource requirements and passes both the node and allocation filters.
 * Applies both nodeFilter and allocationFilter inline during a single pass.
 */
public class FirstFitExecutorSelector implements ExecutorSelector {

	private final ExecutorResourceAllocator allocator;

	@Inject
	public FirstFitExecutorSelector(ExecutorResourceAllocator allocator) {
		this.allocator = allocator;
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
		
		// Single pass: apply nodeFilter, allocate, apply allocationFilter inline
		return nodes.stream()
				.filter(nodeFilter)
				.map(node -> allocator.allocate(node, cpus, memory, gpus, gpuType))
				.filter(Optional::isPresent)
				.map(Optional::get)
				.map(CandidateExecutorAllocation::getAllocatedNode)
				.filter(allocationFilter)
				.findFirst();
	}
}


