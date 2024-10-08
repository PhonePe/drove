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
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import java.util.Collections;
import java.util.Set;

/**
 *
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ResourceConfig {
    public static final ResourceConfig DEFAULT = new ResourceConfig();

    @Builder.Default
    private Set<Integer> osCores = Collections.emptySet();

    @Min(50)
    @Max(100)
    @Builder.Default
    private int exposedMemPercentage = 100;

    @Builder.Default
    private boolean disableNUMAPinning = false;

    /**
     * This setting makes all available Nvidia GPUs on the current executor machine available for any container running on this executor.
     * GPU resources are not discovered on the executor, managed and rationed between containers.
     * Needs to be used in conjunction with tagging to ensure only the applications which require a GPU end up on the executor with GPUs.
     */
    @Builder.Default
    private boolean enableNvidiaGpu = false;

    @Builder.Default
    private Set<String> tags = Collections.emptySet();

    @Builder.Default
    private OverProvisioning overProvisioning = OverProvisioning.DEFAULT;
}
