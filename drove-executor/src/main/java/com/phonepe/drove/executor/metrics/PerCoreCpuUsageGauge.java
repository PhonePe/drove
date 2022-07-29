package com.phonepe.drove.executor.metrics;

import com.codahale.metrics.Gauge;
import com.github.dockerjava.api.model.Statistics;
import com.google.common.util.concurrent.AtomicDouble;
import io.appform.signals.signalhandlers.SignalConsumer;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

import java.util.Collections;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/**
 *
 */
@Slf4j
public class PerCoreCpuUsageGauge implements Gauge<Double>, SignalConsumer<Statistics> {
    private final int numAllocatedCpus;

    public PerCoreCpuUsageGauge(int numAllocatedCpus) {
        this.numAllocatedCpus = numAllocatedCpus;
    }

    @Override
    public Double getValue() {
        return currPercentage.get();
    }

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
        val totalUsage = (long)cpuStats.getCpuUsage().getTotalUsage();
        val systemUsage = (long) Objects.requireNonNullElse(cpuStats.getSystemCpuUsage(), 0L);
        if (null != currUsage.get()) {
            val prev = currUsage.get();
            val cpuDelta = totalUsage - prev.getTotalTime();
            val systemDelta = systemUsage - prev.getSystemTime();
            if (cpuDelta > 0 || systemDelta > 0) {
                val perCpuUsage = Objects.requireNonNullElse(
                        cpuStats.getCpuUsage().getPercpuUsage(), Collections.emptyList());
                currPercentage.set((double) (cpuDelta * 100 * perCpuUsage
                        .size()) / (systemDelta * numAllocatedCpus));
            }
        }
        currUsage.set(new CPUUsage(totalUsage, systemUsage));
    }

    @Value
    private static class CPUUsage {
        long totalTime;
        long systemTime;
    }

    private final AtomicReference<CPUUsage> currUsage = new AtomicReference<>(null);
    private final AtomicDouble currPercentage = new AtomicDouble();
}
