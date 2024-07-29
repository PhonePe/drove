package com.phonepe.drove.executor.resourcemgmt;

import org.junit.jupiter.api.Test;


import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

class ResourceConfigTest {


    @Test
    void validateDefaultValuesAreGeneratedForResourceConfig() {
        var resourceConfig = new ResourceConfig();
        assertEquals(Collections.emptySet(), resourceConfig.getOsCores());
        assertEquals(100, resourceConfig.getExposedMemPercentage());
        assertFalse(resourceConfig.isDisableNUMAPinning());
        assertEquals(Collections.emptySet(), resourceConfig.getTags());
        assertFalse(resourceConfig.getOverProvisioning().isEnabled());
    }

}