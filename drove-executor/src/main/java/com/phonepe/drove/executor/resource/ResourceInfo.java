package com.phonepe.drove.executor.resource;

import com.phonepe.drove.common.model.resources.available.AvailableCPU;
import com.phonepe.drove.common.model.resources.available.AvailableMemory;
import lombok.Value;

/**
 *
 */
@Value
public class ResourceInfo {
    AvailableCPU cpu;
    AvailableMemory memory;
}
