package com.phonepe.drove.executor.metrics;

import com.codahale.metrics.Gauge;
import com.github.dockerjava.api.model.Statistics;
import com.google.common.util.concurrent.AtomicDouble;
import io.appform.signals.signalhandlers.SignalConsumer;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

import java.util.Date;
import java.util.concurrent.atomic.AtomicReference;

/**
 *
 */
@Slf4j
public class AverageCpuUsageGauge implements Gauge<Double>, SignalConsumer<Statistics> {

    @Override
    public Double getValue() {
        return currPercentage.get();
    }

    @Override
    public void consume(Statistics data) {
        if(null == data.getCpuStats()
                || null == data.getCpuStats().getCpuUsage()
                || null == data.getCpuStats().getCpuUsage().getTotalUsage()) {
            return;
        }
        val totalUsage = data.getCpuStats().getCpuUsage().getTotalUsage();
        val systemUsage = data.getCpuStats().getSystemCpuUsage();
        val currTime = new Date().getTime();
        if (null != currUsage.get()) {
            val prev = currUsage.get();
            val cpuDelta = totalUsage - prev.getTotalTime();
            val systemDelta = systemUsage - prev.getSystemTime();
            if (cpuDelta > 0 || systemDelta > 0) {
                currPercentage.set((double) (cpuDelta + systemDelta) / currTime);
            }
        }
        currUsage.set(new CPUUsage(totalUsage, systemUsage, currTime));
        log.debug("PEr CPU Usage: {}", data.getCpuStats().getCpuUsage().getPercpuUsage());
    }

    @Value
    private static class CPUUsage {
        long totalTime;
        long systemTime;
        long lastSyncTime;
    }

    private final AtomicReference<CPUUsage> currUsage = new AtomicReference<>(null);
    private final AtomicDouble currPercentage = new AtomicDouble();
}
