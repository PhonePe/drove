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

import com.phonepe.drove.executor.resourcemgmt.metadata.MetadataConfig;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.jackson.Jacksonized;

import io.dropwizard.validation.ValidationMethod;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import java.util.Collections;
import java.util.Set;

/**
 *
 */
@Data
@AllArgsConstructor
@Builder
@Jacksonized
@NoArgsConstructor
public class ResourceConfig {
    public static final ResourceConfig DEFAULT = ResourceConfig.builder().build();

    @Builder.Default
    private Set<Integer> osCores = Collections.emptySet();

    @Min(50)
    @Max(100)
    @Builder.Default
    private int exposedMemPercentage = 100;

    @Builder.Default
    private boolean disableNUMAPinning = false;

    /**
     * <b>Legacy GPU pass-through mode.</b>
     * When true, all available NVIDIA GPUs are exposed to <em>every</em> container via Docker's {@code --gpus all}.
     * GPUs are <b>not</b> discovered, managed, or rationed between containers.
     * Use in conjunction with executor tagging to ensure only GPU-requiring applications land here.
     * <p>
     * This is independent of {@link #nvidiaGPULoaderConfig} which controls managed GPU discovery and allocation.
     * Both can be used independently; prefer {@link #nvidiaGPULoaderConfig} for new deployments.
     */
    @Builder.Default
    private boolean enableNvidiaGpu = false;

    /**
     * <b>Managed GPU discovery and allocation.</b>
     * When non-null and enabled, the executor discovers NVIDIA GPUs via {@code nvidia-smi} (including MIG slices),
     * registers them with the resource manager, and allocates them per-container on demand.
     * <p>
     * This is independent of {@link #enableNvidiaGpu} (legacy pass-through mode).
     */
    private NvidiaGPULoaderConfig nvidiaGPULoaderConfig;

    @Builder.Default
    private Set<String> tags = Collections.emptySet();

    @Builder.Default
    private MetadataConfig metadata = MetadataConfig.DEFAULT;

    @Builder.Default
    private OverProvisioning overProvisioning = OverProvisioning.DEFAULT;

    @ValidationMethod(message = "enableNvidiaGpu (legacy pass-through) and nvidiaGPULoaderConfig (managed GPU) cannot both be enabled simultaneously")
    public boolean isGpuConfigValid() {
        return !(enableNvidiaGpu && nvidiaGPULoaderConfig != null && nvidiaGPULoaderConfig.isEnabled());
    }
}
