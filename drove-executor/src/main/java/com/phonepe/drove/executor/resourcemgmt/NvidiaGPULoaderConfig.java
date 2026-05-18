package com.phonepe.drove.executor.resourcemgmt;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.Value;

@Value
@AllArgsConstructor
public class NvidiaGPULoaderConfig {
    public static final NvidiaGPULoaderConfig DEFAULT = new NvidiaGPULoaderConfig(false);

    boolean enabled;
}
