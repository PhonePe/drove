package com.phonepe.drove.executor.metrics;

import com.google.common.util.concurrent.AtomicDouble;
import com.phonepe.drove.executor.metrics.corecalc.PercentageBasedCPUUsageGaugeBase;
import lombok.extern.slf4j.Slf4j;

/**
 * Calculates CPU usage per code by overallPercentage/#cores
 */
@Slf4j
public class PerCoreCpuUsageGauge extends PercentageBasedCPUUsageGaugeBase {
    private final int numAllocatedCpus;
    private final AtomicDouble currPercentage = new AtomicDouble();

    public PerCoreCpuUsageGauge(int numAllocatedCpus) {
        super();
        this.numAllocatedCpus = numAllocatedCpus;
    }

    @Override
    public Double getValue() {
        return currPercentage.get();
    }

    @Override
    protected final void consumeOverallPercentage(double overallPercentage) {
        currPercentage.set(overallPercentage / numAllocatedCpus);
    }
}
