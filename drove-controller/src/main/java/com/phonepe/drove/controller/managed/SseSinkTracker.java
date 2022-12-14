package com.phonepe.drove.controller.managed;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.primitives.Ints;
import com.phonepe.drove.common.coverageutils.IgnoreInJacocoGeneratedReport;
import io.dropwizard.lifecycle.Managed;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.jetbrains.annotations.NotNull;
import ru.vyarus.dropwizard.guice.module.installer.order.Order;

import javax.inject.Singleton;
import javax.ws.rs.sse.SseEventSink;
import java.time.Duration;
import java.util.concurrent.*;

/**
 *
 */
@Slf4j
@Singleton
@Order(70)
public class SseSinkTracker implements Managed {
    private static final Duration DEFAULT_TIME_LIMIT = Duration.ofSeconds(60);
    private static final Duration DEFAULT_CHECK_DURATION = Duration.ofSeconds(120);


    @Value
    private static class SSEData implements Delayed {
        SseEventSink sseEventSink;
        long expiryTime;

        public SSEData(SseEventSink sseEventSink, Duration timeToKill) {
            this.sseEventSink = sseEventSink;
            expiryTime = System.currentTimeMillis() + timeToKill.toMillis();
        }

        @Override
        public long getDelay(@NotNull TimeUnit timeUnit) {
            return timeUnit.convert(expiryTime - System.currentTimeMillis(), TimeUnit.MILLISECONDS);
        }

        @Override
        public int compareTo(@NotNull Delayed delayed) {
            return Ints.saturatedCast(this.expiryTime - ((SSEData) delayed).expiryTime);
        }
    }
    private final DelayQueue<SSEData> toBeClosed = new DelayQueue<>();
    private final Duration timeToKill;
    private final Duration checkDuration;

    private final ScheduledExecutorService executorService = Executors.newSingleThreadScheduledExecutor();
    private final ScheduledFuture<?> jobFuture;

    @IgnoreInJacocoGeneratedReport
    public SseSinkTracker() {
        this(DEFAULT_TIME_LIMIT, DEFAULT_CHECK_DURATION);
    }

    @VisibleForTesting
    public SseSinkTracker(Duration timeToKill, Duration checkDuration) {
        this.timeToKill = timeToKill;
        this.checkDuration = checkDuration;
        this.jobFuture = executorService.scheduleWithFixedDelay(() -> {
            var element = (SSEData)null;
            while ((element = toBeClosed.poll()) != null) {
                val eventSink = element.getSseEventSink();
                try {
                    if (!eventSink.isClosed()) {
                        eventSink.close();
                        log.debug("Closed sink at timeout");
                    }
                }
                catch (Exception e) {
                    log.error("Error closing event sink: " + e.getMessage(), e);
                }
            }
            log.trace("No more live SSE connections at this time");
        }, 0, checkDuration.toMillis(), TimeUnit.MILLISECONDS);
    }

    public void register(final SseEventSink sink) {
        toBeClosed.add(new SSEData(sink, timeToKill));
        log.debug("New event sink registered for auto-closure");
    }


    @Override
    public void start() throws Exception {
        //Nothing to do here
    }

    @Override
    public void stop() throws Exception {
        this.jobFuture.cancel(true);
        this.executorService.shutdownNow();
    }

}
