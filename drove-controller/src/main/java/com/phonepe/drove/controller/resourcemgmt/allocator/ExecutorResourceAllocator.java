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

package com.phonepe.drove.controller.resourcemgmt.allocator;

import com.phonepe.drove.controller.resourcemgmt.ExecutorHostInfo;
import com.phonepe.drove.models.application.requirements.GpuType;

import java.util.Optional;

/**
 * Strategy interface for selecting and allocating resources from executor if possible.
 * Implementations can define different selection strategies based on resource characteristics.
 */
public interface ExecutorResourceAllocator {
    /**
     * Select the best NUMA node and allocate resources on it.
     *
     * @param hostInfo        executor host information containing topology and resource state
     * @param requiredCpus    number of CPUs requested
     * @param requiredMemory  memory in MB requested
     * @param requiredGpus    number of GPUs requested (0 if no GPUs needed)
     * @return Optional containing the AllocatedExecutorNode with all resources allocated,
     *         or empty if no suitable candidate found
     */
    Optional<ExecutorAllocation> allocate(
            ExecutorHostInfo hostInfo,
            long requiredCpus,
            long requiredMemory,
            long requiredGpus,
            GpuType requiredGpuType);
}


