package com.phonepe.drove.executor.resourcemgmt;

import org.junit.jupiter.api.Test;


import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

class ResourceConfigTest {
    @Test
    void validationFailsIfNumaPinningIsDisabledWithOverProvisioningConfigurationEnabled() {
        assertFalse(new ResourceConfig()
                .setDisableNUMAPinning(false)
                .setOverProvisioning(new OverProvisioning(true, 10, 10))
                .isOverProvisioningEnabledWithDisablePinning());
    }

    @Test
    void validateDefaultValuesAreGeneratedForResourceConfig() {
        var resourceConfig = new ResourceConfig();
        assertEquals(Collections.emptySet(), resourceConfig.getOsCores());
        assertEquals(100, resourceConfig.getExposedMemPercentage());
        assertFalse(resourceConfig.isDisableNUMAPinning());
        assertEquals(Collections.emptySet(), resourceConfig.getTags());
        assertFalse(resourceConfig.getOverProvisioning().isEnabled());
    }

    @Test
    void validationSucceedsIfNumaPinningIsDisabledWithOverProvisioningConfigurationEnabled() {
        assertTrue(new ResourceConfig()
                .setDisableNUMAPinning(true)
                .setOverProvisioning(new OverProvisioning(true, 10, 10))
                .isOverProvisioningEnabledWithDisablePinning());
    }
}