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

package com.phonepe.drove.executor.resourcemgmt;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class OverProvisioning {

    public static final int DEFAULT_MULTIPLIER = 1;
    private static final int MIN_OVER_PROVISIONING_MULTIPLIER = 1;
    private static final int MAX_OVER_PROVISIONING_MULTIPLIER = 20;

    private boolean enabled;

    @Min(MIN_OVER_PROVISIONING_MULTIPLIER)
    @Max(MAX_OVER_PROVISIONING_MULTIPLIER)
    private int cpuMultiplier = DEFAULT_MULTIPLIER;

    @Min(MIN_OVER_PROVISIONING_MULTIPLIER)
    @Max(MAX_OVER_PROVISIONING_MULTIPLIER)
    private int memoryMultiplier = DEFAULT_MULTIPLIER;

}
