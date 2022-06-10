package com.phonepe.drove.executor.metrics;

import com.phonepe.drove.common.CommonTestUtils;
import io.appform.signals.signals.ScheduledSignal;
import lombok.SneakyThrows;
import lombok.val;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 *
 */
class TimeDiffGaugeTest {

    @Test
    @SneakyThrows
    void testTimeDiffGauge() {
        val ctr = new AtomicInteger();
        val g = new TimeDiffGauge<Void>() {
            @Override
            public void consume(Void data) {}
        };

        try (val incS = new ScheduledSignal(Duration.ofSeconds(1)); val s = new ScheduledSignal(Duration.ofSeconds(5))) {
            val out = new AtomicLong();
            incS.connect(d -> ctr.incrementAndGet());
            s.connect(d -> {
                g.setValue(ctr.longValue() * 10_000);
                out.addAndGet(g.getValue());
            });
            CommonTestUtils.delay(Duration.ofSeconds(15));
            val v = out.get();
            assertTrue(400_000 < v && v < 600_000);
        }
    }

}