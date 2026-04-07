package com.phonepe.drove.controller.resourcemgmt;

import com.google.common.base.Strings;
import com.phonepe.drove.models.application.requirements.GpuType;
import com.phonepe.drove.models.info.GPUInfo;

public final class GPUUtils {

    private GPUUtils() {
    }

    public static boolean matches(GPUInfo gpuInfo, GpuType requestedGpuType) {
        if(requestedGpuType == null) {
            return true;
        }
        if(gpuInfo == null) {
            return false;
        }
        if (gpuInfo.getGpuVendor() != requestedGpuType.getVendor()) {
            return false;
        }
       return requestedGpuType.getModel() == null || Strings.nullToEmpty(gpuInfo.getGpuModel()).equalsIgnoreCase(requestedGpuType.getModel());
    }
}
