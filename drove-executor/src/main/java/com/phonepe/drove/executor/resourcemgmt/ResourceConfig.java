package com.phonepe.drove.executor.resourcemgmt;

import lombok.Data;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotEmpty;
import java.util.Collections;
import java.util.Set;

/**
 *
 */
@Data
public class ResourceConfig {
    public static final ResourceConfig DEFAULT = new ResourceConfig();

    @NotEmpty
    private Set<Integer> osCores = Collections.emptySet();

    @Min(50)
    @Max(100)
    private int exposedMemPercentage = 100;
}
