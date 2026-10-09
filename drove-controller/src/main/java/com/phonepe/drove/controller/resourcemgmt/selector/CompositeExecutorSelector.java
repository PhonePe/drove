/*
 *  Copyright (c) 2024 Original Author(s), PhonePe India Pvt. Ltd.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package com.phonepe.drove.controller.resourcemgmt.selector;

import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

import javax.inject.Inject;
import javax.inject.Named;

import com.phonepe.drove.controller.resourcemgmt.ExecutorHostInfo;
import com.phonepe.drove.controller.utils.ControllerUtils;
import com.phonepe.drove.models.application.requirements.ResourceRequirement;
import com.phonepe.drove.models.info.resources.allocation.AllocatedExecutorNode;

import lombok.val;

/**
 * Composite selector that delegates to the appropriate selector based on resource requirements.
 * If GPU resources are requested, uses the GPU-aware selector; otherwise uses the default selector.
 */
public class CompositeExecutorSelector implements ExecutorSelector {

    public static final String FIRST_FIT_EXEC_SELECTOR = "FIRST_FIT_EXEC_SELECTOR";
    public static final String GPU_AWARE_SELECTOR = "GPU_AWARE_SELECTOR";
    private final ExecutorSelector defaultSelector;
    private final ExecutorSelector gpuSelector;

    @Inject
    public CompositeExecutorSelector(
            @Named(FIRST_FIT_EXEC_SELECTOR) ExecutorSelector defaultSelector,
            @Named(GPU_AWARE_SELECTOR) ExecutorSelector gpuSelector) {
        this.defaultSelector = defaultSelector;
        this.gpuSelector = gpuSelector;
    }

    @Override
    public Optional<AllocatedExecutorNode> select(
            List<ExecutorHostInfo> nodes,
            Predicate<ExecutorHostInfo> nodeFilter,
            List<ResourceRequirement> requirements,
            Predicate<AllocatedExecutorNode> allocationFilter) {
        
        // Choose selector based on GPU requirement
        val selector = ControllerUtils.findGpuRequirement(requirements)
                .filter(r -> r.getCount() > 0)
                .map(r -> gpuSelector)
                .orElse(defaultSelector);
        
        return selector.select(nodes, nodeFilter, requirements, allocationFilter);
    }
}

