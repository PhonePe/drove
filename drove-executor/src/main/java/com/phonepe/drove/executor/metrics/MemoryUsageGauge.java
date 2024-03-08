package com.phonepe.drove.executor.metrics;

import com.codahale.metrics.Gauge;
import com.github.dockerjava.api.model.MemoryStatsConfig;
import com.phonepe.drove.executor.metrics.corecalc.MemoryUsageGaugeBase;

import java.util.concurrent.atomic.AtomicLong;

/**
 *
 */
public class MemoryUsageGauge  extends MemoryUsageGaugeBase implements Gauge<Long> {
    private final AtomicLong usage = new AtomicLong(0L);

    @Override
    public Long getValue() {
        return usage.longValue();
    }

    @Override
    protected void consumeUsage(long memUsage, MemoryStatsConfig mem) {
        usage.set(memUsage);
    }

}
