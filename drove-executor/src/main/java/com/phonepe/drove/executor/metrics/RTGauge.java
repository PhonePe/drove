package com.phonepe.drove.executor.metrics;

import com.codahale.metrics.Gauge;
import com.github.dockerjava.api.model.Statistics;
import io.appform.signals.signalhandlers.SignalConsumer;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

import java.util.concurrent.atomic.AtomicReference;

/**
 *
 */
@Slf4j
public abstract class RTGauge<T> implements Gauge<T>, SignalConsumer<Statistics> {

    private final AtomicReference<T> value;
    private final T defaultValue;

    public RTGauge(T defaultValue) {
        this.value = new AtomicReference<>(defaultValue);
        this.defaultValue = defaultValue;
    }

    @Override
    public T getValue() {
        return value.get();
    }

    protected abstract T extractValue(final Statistics stats);

    @Override
    public void consume(Statistics data) {
        try {
            val newValue = extractValue(data);
            value.set(newValue);
            log.info("Value is {}", value.get());
        }
        catch (Exception e) {
            log.error("Error extracting value", e);
        }
        value.set(defaultValue);
    }
}
