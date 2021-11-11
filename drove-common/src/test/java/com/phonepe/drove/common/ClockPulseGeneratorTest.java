package com.phonepe.drove.common;

import lombok.val;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 *
 */
class ClockPulseGeneratorTest {

    @Test
    void testCPG() {
        val cpg = new ClockPulseGenerator("test", Duration.ofSeconds(0), Duration.ofSeconds(1));
        val ctr = new AtomicInteger();
        cpg.onPulse().connect(now -> ctr.incrementAndGet());
        CommonTestUtils.delay(Duration.ofSeconds(5));
        assertTrue(ctr.get() > 3);
    }

}