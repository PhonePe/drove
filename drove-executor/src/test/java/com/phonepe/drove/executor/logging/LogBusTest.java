package com.phonepe.drove.executor.logging;

import lombok.val;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import static com.phonepe.drove.common.CommonTestUtils.waitUntil;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 *
 */
class LogBusTest {

    @Test
    void testLog() {
        val bus = new LogBus();
        val ctr = new AtomicInteger();
        val listener = new LogBus.LogListener() {
            @Override
            public void consume(LogBus.LogLine data) {
                ctr.incrementAndGet();
            }

            @Override
            public String id() {
                return "test";
            }

        };
        bus.registerLogHandler(listener);
        generateLogs(bus);
        waitUntil(() -> ctr.get() == 100);
        assertEquals(100, ctr.get());
        bus.unregisterLogHandler(listener);
        generateLogs(bus);
        assertEquals(100, ctr.get());
    }

    @Test
    void testLogRegDeregNull() {
        val bus = new LogBus();
        val ctr = new AtomicInteger();
        val listener = new LogBus.LogListener() {
            @Override
            public String id() {
                return null;
            }

            @Override
            public void consume(LogBus.LogLine data) {
                ctr.incrementAndGet();
            }
        };
        bus.registerLogHandler(listener);
        generateLogs(bus);
        assertEquals(0, ctr.get());
        bus.unregisterLogHandler(listener);
        generateLogs(bus);
        assertEquals(0, ctr.get());
        bus.unregisterLogHandler((String) null);
        generateLogs(bus);
        assertEquals(0, ctr.get());
    }

    private static void generateLogs(LogBus bus) {
        IntStream.rangeClosed(1,100).forEach(i -> bus.publish(new LogBus.LogLine("TEST_APP", "TI", LogBus.LogChannel.STDOUT, "M-" + i)));
    }

}