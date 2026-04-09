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


}

