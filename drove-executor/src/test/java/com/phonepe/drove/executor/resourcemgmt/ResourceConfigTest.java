package com.phonepe.drove.executor.resourcemgmt;

import org.junit.jupiter.api.Test;


import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

class ResourceConfigTest {
    @Test
    void validationFailsIfNumaPinningIsDisabledWithOverProvisioningConfigurationEnabled() {
        assertFalse(new ResourceConfig()
                .setDisableNUMAPinning(false)
                .setOverProvisioningConfiguration(new OverProvisioningConfiguration(true, 10, 10))
                .isOverProvisioningEnabledWithDisablePinning());
    }

    @Test
    void validateDefaultValuesAreGeneratedForResourceConfig() {
        var resourceConfig = new ResourceConfig();
        assertEquals(Collections.emptySet(), resourceConfig.getOsCores());
        assertEquals(100, resourceConfig.getExposedMemPercentage());
        assertFalse(resourceConfig.isDisableNUMAPinning());
        assertEquals(Collections.emptySet(), resourceConfig.getTags());
        assertFalse(resourceConfig.getOverProvisioningConfiguration().isOverProvisioningUpEnabled());
    }

    @Test
    void validationSucceedsIfNumaPinningIsDisabledWithOverProvisioningConfigurationEnabled() {
        assertTrue(new ResourceConfig()
                .setDisableNUMAPinning(true)
                .setOverProvisioningConfiguration(new OverProvisioningConfiguration(true, 10, 10))
                .isOverProvisioningEnabledWithDisablePinning());
    }
}