package com.phonepe.drove.executor.resourcemgmt;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class NvidiaGPULoaderConfig {
    public static final NvidiaGPULoaderConfig DEFAULT = new NvidiaGPULoaderConfig(false);

    boolean enabled;

    public NvidiaGPULoaderConfig(boolean enabled) {
        this.enabled = enabled;
    }
}
