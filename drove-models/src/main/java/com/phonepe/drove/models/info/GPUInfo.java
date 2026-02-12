package com.phonepe.drove.models.info;

import lombok.Value;

@Value
public class GPUInfo {
    String gpuId;
    String gpuProvider;
    String gpuModel;
    boolean available;
}
