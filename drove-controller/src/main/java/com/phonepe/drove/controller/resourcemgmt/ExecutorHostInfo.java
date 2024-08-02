/*
 *  Copyright (c) 2022 Original Author(s), PhonePe India Pvt. Ltd.
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
    String executorId;
    ExecutorNodeData nodeData;
    Map<Integer, NumaNodeInfo> nodes;
}
