package com.phonepe.drove.executor.metrics;

import com.codahale.metrics.Gauge;
import com.github.dockerjava.api.model.MemoryStatsConfig;
import com.google.common.util.concurrent.AtomicDouble;
import com.phonepe.drove.executor.metrics.corecalc.MemoryUsageGaugeBase;

import java.util.Objects;

/**
 *
 */
public class MemoryUsagePercentageGauge extends MemoryUsageGaugeBase implements Gauge<Double> {
    private final AtomicDouble percentage = new AtomicDouble(0.0);

    @Override
    public Double getValue() {
        return percentage.doubleValue();
    }

    @Override
    protected void consumeUsage(long memUsage, MemoryStatsConfig mem) {
        percentage.set(((double) memUsage * 100)/ Objects.requireNonNullElse(mem.getLimit(), 1L));
    }

}
