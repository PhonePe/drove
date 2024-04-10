package com.phonepe.drove.executor.metrics;

import com.codahale.metrics.Gauge;
import com.github.dockerjava.api.model.MemoryStatsConfig;
import com.phonepe.drove.executor.metrics.corecalc.MemoryUsageGaugeBase;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

/**
 *
 */
public class MemoryAllocatedGauge extends MemoryUsageGaugeBase implements Gauge<Long> {
    private final AtomicLong allocated = new AtomicLong(0L);

    @Override
    public Long getValue() {
        return allocated.longValue();
    }

    @Override
    protected void consumeUsage(long memUsage, MemoryStatsConfig mem) {
        allocated.set(Objects.requireNonNullElse(mem.getLimit(), 1L));
    }

}
