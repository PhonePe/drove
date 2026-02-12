package com.phonepe.drove.executor.resourcemgmt;

import com.phonepe.drove.executor.resourcemgmt.metadata.MetadataConfig;
import lombok.Value;

@Value
public class NvidiaGPULoaderConfig {
    public static final NvidiaGPULoaderConfig DEFAULT = new NvidiaGPULoaderConfig(false);

    boolean enabled;
}
