package com.phonepe.drove.executor.metrics.corecalc;

import com.github.dockerjava.api.model.MemoryStatsConfig;
import com.github.dockerjava.api.model.Statistics;
import io.appform.signals.signalhandlers.SignalConsumer;
import lombok.val;

import java.util.Objects;

/**
 *
 */
public abstract class MemoryUsageGaugeBase implements SignalConsumer<Statistics> {

    @Override
    public void consume(Statistics data) {
        if (null == data) {
            return;
        }
        val mem = data.getMemoryStats();
        consumeUsage(Objects.requireNonNullElse(mem.getUsage(), 0L) - getCacheUsage(mem), mem);
    }

    protected abstract void consumeUsage(long memUsage, MemoryStatsConfig mem);

    private long getCacheUsage(final MemoryStatsConfig mem) {
        if(null != mem) {
            val stats = mem.getStats();
            if(null != stats) {
                return Objects.requireNonNullElse(stats.getCache(), 0L);
            }
        }
        return 0L;
    }

}
