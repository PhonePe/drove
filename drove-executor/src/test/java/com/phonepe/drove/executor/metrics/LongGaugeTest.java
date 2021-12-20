package com.phonepe.drove.executor.metrics;

import lombok.val;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 *
 */
class LongGaugeTest {

    @Test
    void testGauge() {
        val ctr = new AtomicInteger();
        val g = new LongGauge<Void>() {
            @Override
            public void consume(Void data) {
                setValue(ctr.longValue());
            }
        };

        assertEquals(0L, g.getValue());
        ctr.incrementAndGet();
        g.consume(null);
        assertEquals(1L, g.getValue());
    }
}