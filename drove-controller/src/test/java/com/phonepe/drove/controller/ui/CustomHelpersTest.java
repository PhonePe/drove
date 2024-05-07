package com.phonepe.drove.controller.ui;

import com.phonepe.drove.models.info.resources.allocation.CPUAllocation;
import com.phonepe.drove.models.info.resources.allocation.MemoryAllocation;
import lombok.val;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 *
 */
class CustomHelpersTest {

    @Test
    void testHelpers() {
        val ch = new CustomHelpers();
        assertNotNull(ch.resourceRepr(new CPUAllocation(Map.of(0, Set.of(1,2)))));
        assertNotNull(ch.resourceRepr(new MemoryAllocation(Map.of(0, 1024L))));
    }

}