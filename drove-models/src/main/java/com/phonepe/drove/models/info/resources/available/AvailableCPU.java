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

package com.phonepe.drove.models.info.resources.available;

import com.phonepe.drove.models.application.requirements.ResourceType;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

import java.util.Map;
import java.util.Set;

/**
 *
 */
@Value
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Jacksonized
@Builder
public class AvailableCPU extends AvailableResource {
    /**
     * Storage model:
     * numa node id -> free cpu ids on that node
     */
    Map<Integer, Set<Integer>> freeCores;
    Map<Integer, Set<Integer>> usedCores;

    public AvailableCPU(
            Map<Integer, Set<Integer>> freeCores,
            Map<Integer, Set<Integer>> usedCores) {
        super(ResourceType.CPU);
        this.freeCores = freeCores;
        this.usedCores = usedCores;
    }

    @Override
    public <T> T accept(AvailableResourceVisitor<T> visitor) {
        return visitor.visit(this);
    }
}
