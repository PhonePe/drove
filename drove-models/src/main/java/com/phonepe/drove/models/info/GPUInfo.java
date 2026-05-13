package com.phonepe.drove.models.info;

import com.google.common.base.Preconditions;
import com.phonepe.drove.models.application.requirements.GpuVendor;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

@Value
@Jacksonized
@Builder
public class GPUInfo {
    String gpuId;
    GpuVendor gpuVendor;
    String gpuModel;
    Integer numaNodeAffinity;

    public GPUInfo(String gpuId, GpuVendor gpuVendor, String gpuModel, Integer numaNodeAffinity) {
        Preconditions.checkNotNull(gpuVendor, "GPU vendor cannot be null");
        Preconditions.checkNotNull(gpuId, "GPUId cannot be null");
        Preconditions.checkNotNull(gpuModel, "GPU Model cannot be null");
        this.gpuId = gpuId;
        this.gpuVendor = gpuVendor;
        this.gpuModel = gpuModel;
        this.numaNodeAffinity = numaNodeAffinity;
    }
}
