package com.phonepe.drove.executor.metrics;

import com.codahale.metrics.Gauge;
import io.appform.signals.signalhandlers.SignalConsumer;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

import java.util.concurrent.atomic.AtomicLong;

/**
 * A simple gauge to measure long values. The {@link SignalConsumer#consume(Object)} method needs to be overridden
 * to update the value
 */
@Slf4j
public abstract class TimeDiffGauge<R> implements Gauge<Long>, SignalConsumer<R> {
    private final AtomicLong value = new AtomicLong(0);
    private final AtomicLong lastValue = new AtomicLong(0);
    private final AtomicLong lastUpdated = new AtomicLong();

    protected TimeDiffGauge() {
        //Nothing to do here
    }

    @Override
    public Long getValue() {
        return value.get();
    }

    protected final void setValue(long currValue) {
        if(0 == lastUpdated.get()) {
            value.set(0);
            lastValue.set(currValue);
            lastUpdated.set(System.currentTimeMillis());
            return;
        }
        val currTime = System.currentTimeMillis();
        val period = Math.max(1, currTime - lastUpdated.get());
        val currOut = ((currValue - lastValue.get()) * period) / 1000;
        if(currOut >= 0) {
            value.set(currOut);
        }
        lastValue.set(currValue);
        lastUpdated.set(currTime);
    }
}