package com.phonepe.drove.executor.resourcemgmt;

import io.dropwizard.validation.ValidationMethod;
import lombok.Data;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import java.util.Collections;
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

    private OverProvisioning overProvisioning = new OverProvisioning();

    @ValidationMethod(message = "For over provisioning to be enabled, numa pinning needs to be disabled")
    boolean isOverProvisioningEnabledWithDisablePinning() {
        return !overProvisioning.isEnabled() || disableNUMAPinning;
    }
}
