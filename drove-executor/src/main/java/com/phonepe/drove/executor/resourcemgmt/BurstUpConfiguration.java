package com.phonepe.drove.executor.resourcemgmt;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class BurstUpConfiguration {

    private static final int DEFAULT_MULTIPLIER = 1;

    private boolean burstUpEnabled;

    @Min(1)
    @Max(20)
    private int cpuBurstUpMultiplier = DEFAULT_MULTIPLIER;

    @Min(1)
    @Max(20)
    private int memoryBurstUpMultiplier = DEFAULT_MULTIPLIER;

}
