package com.phonepe.drove.executor.managed;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ContainerStatsObserverGpuMetricsTest {

    @Test
    void testAggregateGpuMetricsForAllocatedDevices() {
        NvidiaGpuMetricsCollector.Snapshot snapshot = new NvidiaGpuMetricsCollector.Snapshot(List.of(
                new NvidiaGpuMetricsCollector.GpuSample("GPU-1", 80D, 500L, 1000L, 65D, 220D),
                new NvidiaGpuMetricsCollector.GpuSample("GPU-2", 40D, 250L, 1000L, 60D, 140D),
                new NvidiaGpuMetricsCollector.GpuSample("GPU-3", 10D, 100L, 1000L, 50D, 90D)
        ));

        ContainerStatsObserver.InstanceGpuMetrics metrics =
                ContainerStatsObserver.aggregateGpuMetrics(Set.of("GPU-1", "GPU-2"), snapshot);

        assertEquals(2, metrics.getAllocatedDeviceCount());
        assertEquals(2, metrics.getObservedDeviceCount());
        assertEquals(60D, metrics.getUtilizationAvgPercentage());
        assertEquals(750L, metrics.getMemoryUsedTotalBytes());
        assertEquals(2000L, metrics.getMemoryTotalTotalBytes());
        assertEquals(37.5D, metrics.getMemoryUtilizationAvgPercentage());
        assertEquals(62.5D, metrics.getTemperatureAvgCelsius());
        assertEquals(360D, metrics.getPowerDrawTotalWatts());
    }

    @Test
    void testAggregateGpuMetricsWhenNoAllocation() {
        ContainerStatsObserver.InstanceGpuMetrics metrics =
                ContainerStatsObserver.aggregateGpuMetrics(Set.of(), new NvidiaGpuMetricsCollector.Snapshot(List.of()));

        assertEquals(0, metrics.getAllocatedDeviceCount());
        assertEquals(0, metrics.getObservedDeviceCount());
        assertEquals(0D, metrics.getUtilizationAvgPercentage());
        assertEquals(0L, metrics.getMemoryUsedTotalBytes());
        assertEquals(0L, metrics.getMemoryTotalTotalBytes());
    }

    @Test
    void testAggregateGpuMetricsWhenAllocatedNotObserved() {
        NvidiaGpuMetricsCollector.Snapshot snapshot = new NvidiaGpuMetricsCollector.Snapshot(List.of(
                new NvidiaGpuMetricsCollector.GpuSample("GPU-10", 80D, 100L, 1000L, 65D, 220D)
        ));

        ContainerStatsObserver.InstanceGpuMetrics metrics =
                ContainerStatsObserver.aggregateGpuMetrics(Set.of("GPU-1"), snapshot);

        assertEquals(1, metrics.getAllocatedDeviceCount());
        assertEquals(0, metrics.getObservedDeviceCount());
        assertEquals(0D, metrics.getUtilizationAvgPercentage());
        assertEquals(0L, metrics.getMemoryUsedTotalBytes());
        assertEquals(0L, metrics.getMemoryTotalTotalBytes());
        assertEquals(0D, metrics.getMemoryUtilizationAvgPercentage());
        assertEquals(0D, metrics.getTemperatureAvgCelsius());
        assertEquals(0D, metrics.getPowerDrawTotalWatts());
    }
}

