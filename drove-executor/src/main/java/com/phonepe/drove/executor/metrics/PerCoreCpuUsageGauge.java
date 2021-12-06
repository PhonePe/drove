package com.phonepe.drove.executor.metrics;

import com.codahale.metrics.Gauge;
import com.github.dockerjava.api.model.Statistics;
import com.google.common.util.concurrent.AtomicDouble;
import io.appform.signals.signalhandlers.SignalConsumer;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

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
        val totalUsage = data.getCpuStats().getCpuUsage().getTotalUsage();
        val systemUsage = data.getCpuStats().getSystemCpuUsage();
        if (null != currUsage.get()) {
            val prev = currUsage.get();
            val cpuDelta = totalUsage - prev.getTotalTime();
            val systemDelta = systemUsage - prev.getSystemTime();
            if (cpuDelta > 0 || systemDelta > 0) {
                currPercentage.set((double) (cpuDelta * 100 * data.getCpuStats()
                        .getCpuUsage()
                        .getPercpuUsage()
                        .size()) / (systemDelta * numAllocatedCpus));
            }
        }
        currUsage.set(new CPUUsage(totalUsage, systemUsage));
        log.debug("PEr CPU Usage: {}", data.getCpuStats().getCpuUsage().getPercpuUsage());
    }

    @Value
    private static class CPUUsage {
        long totalTime;
        long systemTime;
    }

    private final AtomicReference<CPUUsage> currUsage = new AtomicReference<>(null);
    private final AtomicDouble currPercentage = new AtomicDouble();
}
