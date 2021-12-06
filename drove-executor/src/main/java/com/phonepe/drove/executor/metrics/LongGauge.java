package com.phonepe.drove.executor.metrics;

import com.codahale.metrics.Gauge;
import io.appform.signals.signalhandlers.SignalConsumer;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.atomic.AtomicLong;

/**
 *
 */
@Slf4j
public abstract class LongGauge<R> implements Gauge<Long>, SignalConsumer<R> {
    private final String name;
    private final AtomicLong value;

    protected LongGauge(String name) {
        this.name = name;
        this.value = new AtomicLong(0);
    }

    @Override
    public Long getValue() {
        return value.get();
    }

    protected final void setValue(long currValue) {
        value.set(currValue);
    }

/*    protected abstract Long extractValue(final Statistics stats);

    @Override
    public final void consume(Statistics data) {
        try {
            val newValue = extractValue(data);
            value.set(newValue);
        }
        catch (Exception e) {
            log.error("Error extracting value", e);
            value.set(0L);
        }
    }*/
}
