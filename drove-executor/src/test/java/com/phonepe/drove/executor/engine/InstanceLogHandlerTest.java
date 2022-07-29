package com.phonepe.drove.executor.engine;

import com.github.dockerjava.api.model.Frame;
import com.github.dockerjava.api.model.StreamType;
import lombok.val;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.stream.IntStream;

/**
 *
 */
class InstanceLogHandlerTest {

    @Test
    void testLogging() {
        val logHandler = new InstanceLogHandler(Collections.emptyMap());
        logHandler.onNext(new Frame(StreamType.STDOUT, null));
        IntStream.rangeClosed(1, 100)
                .forEach(i -> logHandler.onNext(new Frame(StreamType.STDOUT,
                                                          ("Message " + i).getBytes(StandardCharsets.UTF_8))));
        IntStream.rangeClosed(1, 10)
                .forEach(i -> logHandler.onNext(new Frame(StreamType.STDERR,
                                                          ("Message " + i).getBytes(StandardCharsets.UTF_8))));
    }

}