package com.phonepe.drove.executor.engine;

import com.github.dockerjava.api.model.Frame;
import com.github.dockerjava.api.model.StreamType;
import com.phonepe.drove.executor.logging.LogBus;
import lombok.val;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import static com.phonepe.drove.common.CommonTestUtils.waitUntil;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 *
 */
class InstanceLogHandlerTest {

    @Test
    void testLogging() {
        val bus = new LogBus();
        val stdoutMessagesCount = new AtomicInteger();
        val stderrMessagesCount = new AtomicInteger();
        bus.registerLogHandler(new LogBus.LogListener() {
            @Override
            public String id() {
                return "test";
            }

            @Override
            public void consume(LogBus.LogLine logLine) {
                if (logLine.getLogChannel().equals(LogBus.LogChannel.STDERR)) {
                    stderrMessagesCount.incrementAndGet();
                }
                else {
                    stdoutMessagesCount.incrementAndGet();
                }
            }
        });
        val logHandler = new InstanceLogHandler(Collections.emptyMap(), "TEST_APP", "TI", bus);
        logHandler.onNext(new Frame(StreamType.STDOUT, null));
        IntStream.rangeClosed(1, 100)
                .forEach(i -> logHandler.onNext(new Frame(StreamType.STDOUT,
                                                          ("Message " + i).getBytes(StandardCharsets.UTF_8))));
        IntStream.rangeClosed(1, 10)
                .forEach(i -> logHandler.onNext(new Frame(StreamType.STDERR,
                                                          ("Message " + i).getBytes(StandardCharsets.UTF_8))));
        waitUntil(() -> stderrMessagesCount.get() == 10);

        assertEquals(100, stdoutMessagesCount.get());
        assertEquals(10, stderrMessagesCount.get());
    }

}