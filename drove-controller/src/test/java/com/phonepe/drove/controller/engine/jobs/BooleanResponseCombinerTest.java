package com.phonepe.drove.controller.engine.jobs;

import lombok.val;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 *
 */
class BooleanResponseCombinerTest {

    @Test
    void testCombiner() {
        val c = new BooleanResponseCombiner();
        assertFalse(c.current());
        c.combine(null, true);
        assertTrue(c.current());
        c.combine(null, false);
        assertTrue(c.current());
        val r = c.buildResult("j1");
        assertTrue(r.getResult());
    }

}