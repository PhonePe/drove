package com.phonepe.drove.executor.resourcemgmt;

import lombok.Data;

import javax.validation.constraints.Min;

@Data
public class BurstUpConfiguration {

    private static final int DEFAULT_MULTIPLIER = 1;

    private boolean burstUpEnabled;

    @Min(1)
    private int cpuBurstUpMultiplier = DEFAULT_MULTIPLIER;

    @Min(1)
    private int memoryBurstUpMultiplier = DEFAULT_MULTIPLIER;

}
