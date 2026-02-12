package com.phonepe.drove.executor.resourcemgmt.resourceloaders;

import com.phonepe.drove.models.info.GPUInfo;

import java.util.Set;

public interface GPUResourceLoader {
    Set<GPUInfo> loadGPUInfo();
}
