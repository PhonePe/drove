package com.phonepe.drove.controller.managed;

import com.phonepe.drove.common.CommonTestUtils;
import lombok.Getter;
import lombok.SneakyThrows;
import lombok.val;
import org.junit.jupiter.api.Test;

import javax.ws.rs.sse.OutboundSseEvent;
import javax.ws.rs.sse.SseEventSink;
import java.time.Duration;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 *
 */
class SseSinkTrackerTest {

    private static final class DummySink implements SseEventSink {

        private final boolean status;
        private final boolean throwException;

        @Getter
        private final AtomicBoolean currState;

        private DummySink(boolean status, boolean throwException) {
            this.status = status;
            this.throwException = throwException;
            currState = new AtomicBoolean(this.status);
        }

        @Override
        public boolean isClosed() {
            return status;
        }

        @Override
        public CompletionStage<?> send(OutboundSseEvent event) {
            return null;
        }

        @Override
        public void close() {
            if(throwException) {
                throw new IllegalStateException("Forced Exception for testing");
            }
            currState.set(true);
        }
    }

    @Test
    @SneakyThrows
    void testClosureSuccess() {
        val st = new SseSinkTracker(Duration.ofSeconds(3), Duration.ofSeconds(1));
        st.start();
        val sink = new DummySink(false, false);
        assertFalse(sink.getCurrState().get());
        st.register(sink);
        try {
            CommonTestUtils.waitUntil(() -> sink.getCurrState().get());
            assertTrue(sink.getCurrState().get());
        }
        finally {
            st.stop();
        }
    }

    @Test
    @SneakyThrows
    void testClosureNoOp() {
        val st = new SseSinkTracker(Duration.ofSeconds(3), Duration.ofSeconds(1));
        st.start();
        val sink = new DummySink(true, false);
        assertTrue(sink.getCurrState().get());
        st.register(sink);
        try {
            CommonTestUtils.delay(Duration.ofSeconds(5));
            assertTrue(sink.getCurrState().get());
        }
        finally {
            st.stop();
        }
    }

    @Test
    @SneakyThrows
    void testClosureException() {
        val st = new SseSinkTracker(Duration.ofSeconds(3), Duration.ofSeconds(1));
        st.start();
        val sink = new DummySink(false, true);
        assertFalse(sink.getCurrState().get());
        st.register(sink);
        try {
            CommonTestUtils.delay(Duration.ofSeconds(5));
            assertFalse(sink.getCurrState().get());
        }
        finally {
            st.stop();
        }
    }
}