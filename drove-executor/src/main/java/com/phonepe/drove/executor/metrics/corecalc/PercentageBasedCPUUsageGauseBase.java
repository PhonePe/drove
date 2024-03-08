package com.phonepe.drove.executor.metrics.corecalc;

import com.codahale.metrics.Gauge;
import com.github.dockerjava.api.model.Statistics;
import io.appform.signals.signalhandlers.SignalConsumer;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import lombok.val;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/**
 *
 */
@RequiredArgsConstructor(access = AccessLevel.PROTECTED)
public abstract class PercentageBasedCPUUsageGauseBase implements Gauge<Double>, SignalConsumer<Statistics> {

    private final AtomicReference<CPUUsage> currUsage = new AtomicReference<>(null);

    @Override
    public void consume(Statistics data) {
        if (null == data) {
            return;
        }
        val cpuStats = data.getCpuStats();
        if (null == cpuStats
                || null == cpuStats.getCpuUsage()
                || null == cpuStats.getCpuUsage().getTotalUsage()) {
            currUsage.set(new CPUUsage(0L, 0L));
            return;
        }
        val totalUsage = (long) cpuStats.getCpuUsage().getTotalUsage();
        val systemUsage = (long) Objects.requireNonNullElse(cpuStats.getSystemCpuUsage(), 0L);
        if (null != currUsage.get()) {
            val prev = currUsage.get();
            val cpuDelta = totalUsage - prev.getTotalUsage();
            val systemDelta = systemUsage - prev.getSystemUsage();
            if (cpuDelta > 0 || systemDelta > 0) {
                val onlineCPUs = Objects.requireNonNullElse(cpuStats.getOnlineCpus(), 0L);
                val overallCPUUsagePercentage = ((double) cpuDelta * 100 * onlineCPUs)/ systemDelta;
                consumeOverallPercentage(overallCPUUsagePercentage);
            }
        }
        currUsage.set(new CPUUsage(totalUsage, systemUsage));
    }

    protected abstract void consumeOverallPercentage(double overallPercentage);

    @Value
    private static class CPUUsage {
        long totalUsage;
        long systemUsage;
    }


}
