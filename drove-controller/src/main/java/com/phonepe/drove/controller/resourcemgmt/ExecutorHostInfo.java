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

package com.phonepe.drove.controller.resourcemgmt;

import com.phonepe.drove.models.info.nodedata.ExecutorNodeData;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Value;
import lombok.val;

import java.util.HashMap;
import java.util.Map;

/**
 *
 */
@Data
@AllArgsConstructor
public class ExecutorHostInfo {
    public enum CoreState {
        UNKNOWN,
        UNAVAILABLE,
        FREE,
        ALLOCATED,
        IN_USE
    }

    @Data
    public static final class MemInfo {
        long available = 0L;
        long used = 0L;
    }

    @Value
    public static class NumaNodeInfo {
        Map<Integer, CoreState> cores = new HashMap<>();
        MemInfo memory = new MemInfo();
    }

    @Value
    public static class HostGPUInfo {
        Map<String, CoreState> gpus = new HashMap<>();
    }

    String executorId;
    ExecutorNodeData nodeData;
    Map<Integer, NumaNodeInfo> nodes;
    HostGPUInfo gpuInfo;

    /**
     * Makes a copy that does not share mutable state with this object. The copy
     * contains new core maps, memory info and GPU maps. The copy shares
     * nodeData, because nodeData is immutable.
     */
    public ExecutorHostInfo shallowCopy() {
        val nodesCopy = new HashMap<Integer, NumaNodeInfo>();
        if (null != nodes) {
            nodes.forEach((numaNodeId, numaNode) -> {
                val numaNodeCopy = new NumaNodeInfo();
                numaNodeCopy.getCores().putAll(numaNode.getCores());
                numaNodeCopy.getMemory().setAvailable(numaNode.getMemory().getAvailable());
                numaNodeCopy.getMemory().setUsed(numaNode.getMemory().getUsed());
                nodesCopy.put(numaNodeId, numaNodeCopy);
            });
        }
        val gpuInfoCopy = new HostGPUInfo();
        if (null != gpuInfo) {
            gpuInfoCopy.getGpus().putAll(gpuInfo.getGpus());
        }
        return new ExecutorHostInfo(executorId, nodeData, nodesCopy, gpuInfoCopy);
    }
}
