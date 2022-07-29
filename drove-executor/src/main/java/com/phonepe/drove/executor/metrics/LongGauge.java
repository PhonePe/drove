package com.phonepe.drove.executor.metrics;

import com.codahale.metrics.Gauge;
import io.appform.signals.signalhandlers.SignalConsumer;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.atomic.AtomicLong;

/**
 * A simple gauge to measure long values. The {@link SignalConsumer#consume(Object)} method needs to be overridden
 * to update the value
 */
@Slf4j
public abstract class LongGauge<R> implements Gauge<Long>, SignalConsumer<R> {
    private final AtomicLong value;

    protected LongGauge() {
        this.value = new AtomicLong(0);
    }

    @Override
    public Long getValue() {
        return value.get();
    }

    protected final void setValue(long currValue) {
        value.set(currValue);
    }
}
