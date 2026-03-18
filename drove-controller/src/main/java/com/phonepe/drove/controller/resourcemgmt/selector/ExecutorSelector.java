package com.phonepe.drove.controller.resourcemgmt.selector;

import com.phonepe.drove.controller.resourcemgmt.AllocatedExecutorNode;
import com.phonepe.drove.controller.resourcemgmt.ExecutorHostInfo;
import com.phonepe.drove.models.application.requirements.CPURequirement;
import com.phonepe.drove.models.application.requirements.GPURequirement;
import com.phonepe.drove.models.application.requirements.MemoryRequirement;
import com.phonepe.drove.models.application.requirements.ResourceRequirement;

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
		return ((CPURequirement) extractRequirement(resources, "CPU")).getCount();
	}

	/**
	 * Utility method to extract memory requirement in MB from resource requirements list.
	 */
	static long extractMemoryRequirement(List<ResourceRequirement> resources) {
		return ((MemoryRequirement) extractRequirement(resources, "MEMORY")).getSizeInMB();
	}

	/**
	 * Utility method to extract GPU requirement count from resource requirements list.
	 */
	static long extractGpuRequirement(List<ResourceRequirement> resources) {
		return ((GPURequirement) extractRequirement(resources, "GPU")).getCount();
	}

	/**
	 * Utility method to extract specific resource requirement from the list.
	 */
	static ResourceRequirement extractRequirement(List<ResourceRequirement> resources, String typeName) {
		return resources.stream()
				.filter(req -> req.getType().name().equals(typeName))
				.findFirst()
				.orElseThrow(() -> new IllegalArgumentException("Resource requirement of type " + typeName + " is required"));
	}
}

