package com.phonepe.drove.executor.engine;

import com.codahale.metrics.SharedMetricRegistries;
import com.github.dockerjava.api.model.Frame;
import com.github.dockerjava.api.model.StreamType;
import com.phonepe.drove.executor.ExecutorTestingUtils;
import lombok.val;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.fail;

/**
 *
 */
class InstanceLogHandlerTest {

    @Test
    void testLogging() {
        try {
            val logHandler = new InstanceLogHandler(Collections.emptyMap(),
                                                    ExecutorTestingUtils.createExecutorAppInstanceInfo(ExecutorTestingUtils.testAppInstanceSpec("blah"), 8080),
                                                    SharedMetricRegistries.getOrCreate("test"));
            logHandler.onNext(new Frame(StreamType.STDOUT, null));
            IntStream.rangeClosed(1, 100)
                    .forEach(i -> logHandler.onNext(new Frame(StreamType.STDOUT,
                                                              ("Message " + i).getBytes(StandardCharsets.UTF_8))));
            IntStream.rangeClosed(1, 10)
                    .forEach(i -> logHandler.onNext(new Frame(StreamType.STDERR,
                                                              ("Message " + i).getBytes(StandardCharsets.UTF_8))));
        }
        catch (Exception e) {
            fail("Should not have filed with: " + e.getMessage());
        }
    }

}