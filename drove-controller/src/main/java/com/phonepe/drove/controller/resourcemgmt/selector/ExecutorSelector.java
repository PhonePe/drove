package com.phonepe.drove.controller.resourcemgmt.selector;

import com.phonepe.drove.controller.resourcemgmt.AllocatedExecutorNode;
import com.phonepe.drove.controller.resourcemgmt.ExecutorHostInfo;
import com.phonepe.drove.models.application.requirements.*;

import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * Strategy interface for selecting an executor from the available host list.
 * Implementations should apply both nodeFilter and allocationFilter inline during a single pass
 * to avoid multiple iterations over the nodes list.
 */
public interface ExecutorSelector {

	/**
	 * Select the best executor from available nodes.
	 * Implementations must apply filtering inline during selection for efficiency (single pass).
	 *
	 * @param nodes              list of available executor host information
	 * @param nodeFilter         predicate to filter valid nodes
	 * @param requirements       list of resource requirements
	 * @param allocationFilter   predicate to filter valid allocations
	 * @return Optional containing the selected AllocatedExecutorNode, or empty if none match filters
	 */
	Optional<AllocatedExecutorNode> select(
			List<ExecutorHostInfo> nodes,
			Predicate<ExecutorHostInfo> nodeFilter,
			List<ResourceRequirement> requirements,
			Predicate<AllocatedExecutorNode> allocationFilter);

	/**
	 * Utility method to extract CPU requirement count from resource requirements list.
	 */
	static long extractCpuRequirement(List<ResourceRequirement> resources) {
		return ((CPURequirement) extractRequirement(resources, ResourceType.CPU)).getCount();
	}

	/**
	 * Utility method to extract memory requirement in MB from resource requirements list.
	 */
	static long extractMemoryRequirement(List<ResourceRequirement> resources) {
		return ((MemoryRequirement) extractRequirement(resources, ResourceType.MEMORY)).getSizeInMB();
	}



	/**
	 * Utility method to find the GPU requirement, returning empty if none is present.
	 */
	static Optional<GPURequirement> findGpuRequirement(List<ResourceRequirement> resources) {
		return resources.stream()
				.filter(req -> req.getType() == ResourceType.GPU)
				.map(GPURequirement.class::cast)
				.findFirst();
	}

	/**
	 * Utility method to extract specific resource requirement from the list.
	 */
	static ResourceRequirement extractRequirement(List<ResourceRequirement> resources, ResourceType type) {
		return resources.stream()
				.filter(req -> req.getType() == type)
				.findFirst()
				.orElseThrow(() -> new IllegalArgumentException("Resource requirement of type " + type + " is required"));
	}
}

