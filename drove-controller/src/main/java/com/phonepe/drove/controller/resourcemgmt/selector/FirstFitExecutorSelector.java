package com.phonepe.drove.controller.resourcemgmt.selector;

import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

import javax.inject.Inject;
import javax.inject.Singleton;

import com.phonepe.drove.controller.resourcemgmt.ExecutorHostInfo;
import com.phonepe.drove.controller.resourcemgmt.allocator.CandidateExecutorAllocation;
import com.phonepe.drove.controller.resourcemgmt.allocator.ExecutorResourceAllocator;
import com.phonepe.drove.controller.utils.ControllerUtils;
import com.phonepe.drove.models.application.requirements.GPURequirement;
import com.phonepe.drove.models.application.requirements.ResourceRequirement;
import com.phonepe.drove.models.info.resources.allocation.AllocatedExecutorNode;

import lombok.AllArgsConstructor;
import lombok.val;

/**
 * Selects the first executor node that satisfies the resource requirements and passes both the node and allocation filters.
 * Applies both nodeFilter and allocationFilter inline during a single pass.
 */
@Singleton
@AllArgsConstructor(onConstructor = @__(@Inject))
public class FirstFitExecutorSelector implements ExecutorSelector {

	private final ExecutorResourceAllocator allocator;

	@Override
	public Optional<AllocatedExecutorNode> select(
			List<ExecutorHostInfo> nodes,
			Predicate<ExecutorHostInfo> nodeFilter,
			List<ResourceRequirement> requirements,
			Predicate<AllocatedExecutorNode> allocationFilter) {
		
		val cpus = ControllerUtils.extractCpuRequirement(requirements);
		val memory = ControllerUtils.extractMemoryRequirement(requirements);
		val gpuReq = ControllerUtils.findGpuRequirement(requirements);
		val gpus = gpuReq.map(GPURequirement::getCount).orElse(0);
		val gpuType = gpuReq.map(GPURequirement::getGpuType).orElse(null);
		
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


