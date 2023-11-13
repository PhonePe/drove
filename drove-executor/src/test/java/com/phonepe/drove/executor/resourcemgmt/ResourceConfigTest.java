package com.phonepe.drove.executor.resourcemgmt;

import org.junit.jupiter.api.Test;


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
    void validationSucceedsIfNumaPinningIsDisabledWithBurstUpConfigurationEnabled() {
        assertTrue(new ResourceConfig()
                .setDisableNUMAPinning(true)
                .setBurstUpConfiguration(new BurstUpConfiguration(true, 10, 10))
                .isBurstAbleEnabledWithDisablePinning());
    }
}