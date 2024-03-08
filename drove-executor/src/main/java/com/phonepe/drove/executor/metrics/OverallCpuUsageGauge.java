package com.phonepe.drove.executor.metrics;

import com.google.common.util.concurrent.AtomicDouble;
import com.phonepe.drove.executor.metrics.corecalc.PercentageBasedCPUUsageGaugeBase;
import lombok.extern.slf4j.Slf4j;

/**
 * Calculates according to <a href="https://docs.docker.com/engine/api/v1.43/#tag/Container/operation/ContainerExport">...</a>
 */
@Slf4j
public class OverallCpuUsageGauge extends PercentageBasedCPUUsageGaugeBase {
    private final AtomicDouble currPercentage = new AtomicDouble();

    public OverallCpuUsageGauge() {
        super();
    }

    @Override
    public Double getValue() {
        return currPercentage.get();
    }

    @Override
    protected final void consumeOverallPercentage(double overallPercentage) {
        currPercentage.set(overallPercentage);
    }
}
