package com.phonepe.drove.common;

import io.dropwizard.util.Duration;
import lombok.val;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 *
 */
class ClockPulseGeneratorTest {

    @Test
    void testCPG() {
        val cpg = new ClockPulseGenerator("test", Duration.seconds(0), Duration.seconds(1));
        val ctr = new AtomicInteger();
        cpg.onPulse().connect(now -> ctr.incrementAndGet());
        CommonTestUtils.delay(Duration.seconds(5));
        assertTrue(ctr.get() > 3);
    }

}