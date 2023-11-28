package com.phonepe.drove.executor.resourcemgmt;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class OverProvisioning {

    public static final int DEFAULT_MULTIPLIER = 1;
    private static final int MIN_OVER_PROVISIONING_MULTIPLIER = 1;
    private static final int MAX_OVER_PROVISIONING_MULTIPLIER = 20;

    private boolean enabled;

    @Min(MIN_OVER_PROVISIONING_MULTIPLIER)
    @Max(MAX_OVER_PROVISIONING_MULTIPLIER)
    private int cpuMultiplier = DEFAULT_MULTIPLIER;

    @Min(MIN_OVER_PROVISIONING_MULTIPLIER)
    @Max(MAX_OVER_PROVISIONING_MULTIPLIER)
    private int memoryMultiplier = DEFAULT_MULTIPLIER;

}
