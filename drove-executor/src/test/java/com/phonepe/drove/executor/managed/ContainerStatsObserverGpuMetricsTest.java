/*
 *  Copyright (c) 2024 Original Author(s), PhonePe India Pvt. Ltd.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package com.phonepe.drove.executor.managed;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ContainerStatsObserverGpuMetricsTest {

    @Test
    void testAggregateGpuMetricsForAllocatedDevices() {
        NvidiaGpuMetricsCollector.Snapshot snapshot = new NvidiaGpuMetricsCollector.Snapshot(List.of(
                new NvidiaGpuMetricsCollector.GpuSample("GPU-1", 80D, 80D,500L, 1000L, 65D, 220D, 70D, 1400D, 60D, 0L),
                new NvidiaGpuMetricsCollector.GpuSample("GPU-2", 40D, 30D,250L, 1000L, 60D, 140D, 55D, 1380D, 30D, 0L),
                new NvidiaGpuMetricsCollector.GpuSample("GPU-3", 10D, 20D,100L, 1000L, 50D, 90D, 48D, 1200D, 15D, 1L)
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
        assertEquals(62.5D, metrics.getMemoryTempAvgCelsius());
        assertEquals(1390D, metrics.getSmClockAvgMhz());
        assertEquals(45D, metrics.getMemoryControllerUtilizationAvgPercentage());
        assertEquals(0L, metrics.getThermalThrottleCount());
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
        assertEquals(0D, metrics.getMemoryUtilizationAvgPercentage());
        assertEquals(0D, metrics.getTemperatureAvgCelsius());
        assertEquals(0D, metrics.getPowerDrawTotalWatts());
        assertEquals(0D, metrics.getMemoryTempAvgCelsius());
        assertEquals(0D, metrics.getSmClockAvgMhz());
        assertEquals(0D, metrics.getMemoryControllerUtilizationAvgPercentage());
        assertEquals(0L, metrics.getThermalThrottleCount());
    }

    @Test
    void testAggregateGpuMetricsWhenAllocatedNotObserved() {
        NvidiaGpuMetricsCollector.Snapshot snapshot = new NvidiaGpuMetricsCollector.Snapshot(List.of(
                new NvidiaGpuMetricsCollector.GpuSample("GPU-10", 80D, 10D,100L, 1000L, 65D, 220D, 70D, 1400D, 60D, 0L)
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
        assertEquals(0D, metrics.getMemoryTempAvgCelsius());
        assertEquals(0D, metrics.getSmClockAvgMhz());
        assertEquals(0D, metrics.getMemoryControllerUtilizationAvgPercentage());
        assertEquals(0L, metrics.getThermalThrottleCount());
    }

    @Test
    void testAggregateGpuMetricsWithThermalThrottle() {
        NvidiaGpuMetricsCollector.Snapshot snapshot = new NvidiaGpuMetricsCollector.Snapshot(List.of(
                new NvidiaGpuMetricsCollector.GpuSample("GPU-1", 80D, 10D,500L, 1000L, 65D, 220D, 70D, 1400D, 60D, 1L),
                new NvidiaGpuMetricsCollector.GpuSample("GPU-2", 40D, 25D,250L, 1000L, 60D, 140D, 55D, 1380D, 30D, 1L)
        ));

        ContainerStatsObserver.InstanceGpuMetrics metrics =
                ContainerStatsObserver.aggregateGpuMetrics(Set.of("GPU-1", "GPU-2"), snapshot);

        assertEquals(2L, metrics.getThermalThrottleCount());
        assertEquals(62.5D, metrics.getMemoryTempAvgCelsius());
        assertEquals(1390D, metrics.getSmClockAvgMhz());
        assertEquals(45D, metrics.getMemoryControllerUtilizationAvgPercentage());
    }
}
