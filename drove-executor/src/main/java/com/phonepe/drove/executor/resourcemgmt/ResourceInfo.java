package com.phonepe.drove.executor.resourcemgmt;

import com.phonepe.drove.models.info.resources.PhysicalLayout;
import com.phonepe.drove.models.info.resources.available.AvailableCPU;
import com.phonepe.drove.models.info.resources.available.AvailableMemory;
import lombok.Value;

/**
 *
 */
@Value
public class ResourceInfo {
    AvailableCPU cpu;
    AvailableMemory memory;
    PhysicalLayout physicalLayout;
}
