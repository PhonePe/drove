package com.phonepe.drove.executor.resourcemgmt;

import com.phonepe.drove.auth.config.ClusterAuthenticationConfig;
import io.dropwizard.validation.ValidationMethod;
import lombok.Data;
import lombok.val;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 *
 */
@Data
public class ResourceConfig {
    public static final ResourceConfig DEFAULT = new ResourceConfig();

    @NotNull
    private Set<Integer> osCores = Collections.emptySet();

    @Min(50)
    @Max(100)
    private int exposedMemPercentage = 100;

    private boolean disableNUMAPinning;

    private Set<String> tags = Collections.emptySet();

    private BurstUpConfiguration burstUpConfiguration = new BurstUpConfiguration();

    @ValidationMethod(message = "Burst up can't be enabled without numa pinning disabled")
    boolean isBurstAbleEnabledWithDisablePinning() {
        return !burstUpConfiguration.isBurstUpEnabled() || disableNUMAPinning;
    }
}
