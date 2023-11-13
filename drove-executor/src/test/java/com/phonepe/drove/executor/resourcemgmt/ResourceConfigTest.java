package com.phonepe.drove.executor.resourcemgmt;

import org.junit.jupiter.api.Test;


import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

class ResourceConfigTest {
    @Test
    void validationFailsIfNumaPinningIsDisabledWithBurstUpConfigurationEnabled() {
        assertFalse(new ResourceConfig()
                .setDisableNUMAPinning(false)
                .setBurstUpConfiguration(new BurstUpConfiguration(true, 10, 10))
                .isBurstAbleEnabledWithDisablePinning());
    }

    @Test
    void validateDefaultValuesAreGeneratedForResourceConfig() {
        var resourceConfig = new ResourceConfig();
        assertEquals(Collections.emptySet(), resourceConfig.getOsCores());
        assertEquals(100, resourceConfig.getExposedMemPercentage());
        assertFalse(resourceConfig.isDisableNUMAPinning());
        assertEquals(Collections.emptySet(), resourceConfig.getTags());
        assertFalse(resourceConfig.getBurstUpConfiguration().isBurstUpEnabled());
    }

    @Test
    void validationSucceedsIfNumaPinningIsDisabledWithBurstUpConfigurationEnabled() {
        assertTrue(new ResourceConfig()
                .setDisableNUMAPinning(true)
                .setBurstUpConfiguration(new BurstUpConfiguration(true, 10, 10))
                .isBurstAbleEnabledWithDisablePinning());
    }
}