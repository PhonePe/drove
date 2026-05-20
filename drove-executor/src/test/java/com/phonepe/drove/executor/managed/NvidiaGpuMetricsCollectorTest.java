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

import com.codahale.metrics.MetricRegistry;
import com.phonepe.drove.executor.resourcemgmt.NvidiaGPULoaderConfig;
import com.phonepe.drove.executor.resourcemgmt.ResourceConfig;
import com.phonepe.drove.executor.resourcemgmt.ResourceManager;
import com.phonepe.drove.models.info.resources.available.AvailableGPU;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Duration;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class NvidiaGpuMetricsCollectorTest {

    @Test
    void testParseSnapshot() throws Exception {
        String xml = """
                <nvidia_smi_log>
                  <gpu>
                    <uuid>GPU-1111-AAAA</uuid>
                    <utilization>
                      <gpu_util>78 %</gpu_util>
                      <memory_util>45 %</memory_util>
                    </utilization>
                    <fb_memory_usage>
                      <total>40536 MiB</total>
                      <used>10134 MiB</used>
                    </fb_memory_usage>
                    <temperature>
                      <gpu_temp>61 C</gpu_temp>
                      <memory_temp>65 C</memory_temp>
                    </temperature>
                    <gpu_power_readings>
                      <average_power_draw>210.5 W</average_power_draw>
                    </gpu_power_readings>
                    <clocks>
                      <sm_clock>1410 MHz</sm_clock>
                    </clocks>
                    <clocks_event_reasons>
                      <clocks_event_reason_hw_thermal_slowdown>Not Active</clocks_event_reason_hw_thermal_slowdown>
                      <clocks_event_reason_sw_thermal_slowdown>Not Active</clocks_event_reason_sw_thermal_slowdown>
                    </clocks_event_reasons>
                  </gpu>
                  <gpu>
                    <uuid>GPU-2222-BBBB</uuid>
                    <utilization>
                      <gpu_util>56 %</gpu_util>
                      <memory_util>30 %</memory_util>
                    </utilization>
                    <fb_memory_usage>
                      <total>40536 MiB</total>
                      <used>8100 MiB</used>
                    </fb_memory_usage>
                    <temperature>
                      <gpu_temp>59 C</gpu_temp>
                      <memory_temp>62 C</memory_temp>
                    </temperature>
                    <gpu_power_readings>
                      <power_draw>185.0 W</power_draw>
                    </gpu_power_readings>
                    <clocks>
                      <sm_clock>1380 MHz</sm_clock>
                    </clocks>
                    <clocks_event_reasons>
                      <clocks_event_reason_hw_thermal_slowdown>Active</clocks_event_reason_hw_thermal_slowdown>
                      <clocks_event_reason_sw_thermal_slowdown>Not Active</clocks_event_reason_sw_thermal_slowdown>
                    </clocks_event_reasons>
                  </gpu>
                </nvidia_smi_log>
                """;

        NvidiaGpuMetricsCollector.Snapshot snapshot = NvidiaGpuMetricsCollector.parseSnapshot(xml);

        assertEquals(2, snapshot.gpus().size());
        assertEquals("GPU-1111-AAAA", snapshot.gpus().get(0).gpuId());
        assertEquals(78.0, snapshot.gpus().get(0).gpuUtilizationPct());
        assertEquals(10626269184L, snapshot.gpus().get(0).memoryUsedBytes());
        assertEquals(42505076736L, snapshot.gpus().get(0).memoryTotalBytes());
        assertEquals(61.0, snapshot.gpus().get(0).temperatureC());
        assertEquals(210.5, snapshot.gpus().get(0).powerDrawWatts());
        assertEquals(65.0, snapshot.gpus().get(0).memoryTempC());
        assertEquals(1410.0, snapshot.gpus().get(0).smClockMhz());
        assertEquals(45.0, snapshot.gpus().get(0).memoryControllerUtilizationPct());
        assertEquals(0L, snapshot.gpus().get(0).thermalThrottled());

        assertEquals(185.0, snapshot.gpus().get(1).powerDrawWatts());
        assertEquals(62.0, snapshot.gpus().get(1).memoryTempC());
        assertEquals(1380.0, snapshot.gpus().get(1).smClockMhz());
        assertEquals(30.0, snapshot.gpus().get(1).memoryControllerUtilizationPct());
        assertEquals(1L, snapshot.gpus().get(1).thermalThrottled());
    }

    @Test
    void testPublishSnapshot() {
        MetricRegistry metricRegistry = new MetricRegistry();
        ResourceConfig config = new ResourceConfig();
        config.setNvidiaGPULoaderConfig(new NvidiaGPULoaderConfig(true));

        ResourceManager resourceManager = mock(ResourceManager.class);
        when(resourceManager.currentState()).thenReturn(new com.phonepe.drove.executor.resourcemgmt.ResourceInfo(
                null,
                null,
                new AvailableGPU(List.of("GPU-1111-AAAA", "GPU-2222-BBBB"), List.of("GPU-2222-BBBB")),
                null));

        NvidiaGpuMetricsCollector collector =
                new NvidiaGpuMetricsCollector(metricRegistry, config, resourceManager, Duration.ofSeconds(30));

        collector.publish(new NvidiaGpuMetricsCollector.Snapshot(List.of(
                new NvidiaGpuMetricsCollector.GpuSample("GPU-1111-AAAA", 70D, 100L, 1000L, 55D, 200D, 60D, 1400D, 40D, 0L),
                new NvidiaGpuMetricsCollector.GpuSample("GPU-2222-BBBB", 30D, 200L, 2000L, 52D, 150D, 58D, 1350D, 25D, 1L)
        )));

        assertEquals(2L, metricRegistry.getGauges().get("com.phonepe.drove.executor.gpu.count").getValue());
        assertEquals(50D,
                metricRegistry.getGauges().get("com.phonepe.drove.executor.gpu.utilization.gpu.average_percentage").getValue());
        assertEquals(300L,
                metricRegistry.getGauges().get("com.phonepe.drove.executor.gpu.memory.used.total_bytes").getValue());
        assertEquals(3000L,
                metricRegistry.getGauges().get("com.phonepe.drove.executor.gpu.memory.total.total_bytes").getValue());
        assertEquals(2L,
                metricRegistry.getGauges().get("com.phonepe.drove.executor.gpu.free_count").getValue());
        assertEquals(1L,
                metricRegistry.getGauges().get("com.phonepe.drove.executor.gpu.used_count").getValue());
        assertEquals(70D,
                metricRegistry.getGauges().get("com.phonepe.drove.executor.gpu.device.GPU_1111_AAAA.utilization.gpu_percentage").getValue());
        assertEquals(10D,
                metricRegistry.getGauges().get("com.phonepe.drove.executor.gpu.device.GPU_1111_AAAA.memory.utilization_percentage").getValue());
        assertEquals(59D,
                metricRegistry.getGauges().get("com.phonepe.drove.executor.gpu.memory.temperature.average_celsius").getValue());
        assertEquals(1375D,
                metricRegistry.getGauges().get("com.phonepe.drove.executor.gpu.sm_clock.average_mhz").getValue());
        assertEquals(32.5D,
                metricRegistry.getGauges().get("com.phonepe.drove.executor.gpu.memory.controller_utilization.average_percentage").getValue());
        assertEquals(1L,
                metricRegistry.getGauges().get("com.phonepe.drove.executor.gpu.thermal.throttle_count").getValue());
        assertEquals(60D,
                metricRegistry.getGauges().get("com.phonepe.drove.executor.gpu.device.GPU_1111_AAAA.memory.temperature.celsius").getValue());
        assertEquals(1400D,
                metricRegistry.getGauges().get("com.phonepe.drove.executor.gpu.device.GPU_1111_AAAA.sm_clock.mhz").getValue());
        assertEquals(40D,
                metricRegistry.getGauges().get("com.phonepe.drove.executor.gpu.device.GPU_1111_AAAA.memory.controller_utilization.percentage").getValue());
        assertEquals(0L,
                metricRegistry.getGauges().get("com.phonepe.drove.executor.gpu.device.GPU_1111_AAAA.thermal.throttled").getValue());
        assertEquals(1L,
                metricRegistry.getGauges().get("com.phonepe.drove.executor.gpu.device.GPU_2222_BBBB.thermal.throttled").getValue());
    }

    @Test
    void testParseErrorSetsScrapeSuccessToZero() {
        MetricRegistry metricRegistry = new MetricRegistry();
        ResourceConfig config = new ResourceConfig();
        config.setNvidiaGPULoaderConfig(new NvidiaGPULoaderConfig(true));

        ResourceManager resourceManager = mock(ResourceManager.class);
        when(resourceManager.currentState()).thenReturn(new com.phonepe.drove.executor.resourcemgmt.ResourceInfo(
                null, null, new AvailableGPU(List.of(), List.of()), null));

        NvidiaGpuMetricsCollector collector =
                new NvidiaGpuMetricsCollector(metricRegistry, config, resourceManager, Duration.ofSeconds(30)) {
                    @Override
                    String queryNvidiaSmi() {
                        return "not valid xml";
                    }
                };

        collector.collect(new Date());

        assertEquals(0L,
                metricRegistry.getGauges().get("com.phonepe.drove.executor.gpu.scrape.success").getValue());
    }

    @Test
    void testInterruptedExceptionSetsScrapeSuccessToZero() {
        MetricRegistry metricRegistry = new MetricRegistry();
        ResourceConfig config = new ResourceConfig();
        config.setNvidiaGPULoaderConfig(new NvidiaGPULoaderConfig(true));

        ResourceManager resourceManager = mock(ResourceManager.class);
        when(resourceManager.currentState()).thenReturn(new com.phonepe.drove.executor.resourcemgmt.ResourceInfo(
                null, null, new AvailableGPU(List.of(), List.of()), null));

        NvidiaGpuMetricsCollector collector =
                new NvidiaGpuMetricsCollector(metricRegistry, config, resourceManager, Duration.ofSeconds(30)) {
                    @Override
                    String queryNvidiaSmi() throws InterruptedException {
                        throw new InterruptedException("interrupted");
                    }
                };

        collector.collect(new Date());

        assertEquals(0L,
                metricRegistry.getGauges().get("com.phonepe.drove.executor.gpu.scrape.success").getValue());
        assertTrue(Thread.interrupted(), "Thread should be re-interrupted after InterruptedException");
    }

    @Test
    void testGenericIOExceptionSetsScrapeSuccessToZero() {
        MetricRegistry metricRegistry = new MetricRegistry();
        ResourceConfig config = new ResourceConfig();
        config.setNvidiaGPULoaderConfig(new NvidiaGPULoaderConfig(true));

        ResourceManager resourceManager = mock(ResourceManager.class);
        when(resourceManager.currentState()).thenReturn(new com.phonepe.drove.executor.resourcemgmt.ResourceInfo(
                null, null, new AvailableGPU(List.of(), List.of()), null));

        NvidiaGpuMetricsCollector collector =
                new NvidiaGpuMetricsCollector(metricRegistry, config, resourceManager, Duration.ofSeconds(30)) {
                    @Override
                    String queryNvidiaSmi() throws IOException {
                        throw new IOException("some generic error");
                    }
                };

        collector.collect(new Date());

        assertEquals(0L,
                metricRegistry.getGauges().get("com.phonepe.drove.executor.gpu.scrape.success").getValue());
    }

    @Test
    void testStartDoesNothingWhenDisabled() {
        MetricRegistry metricRegistry = new MetricRegistry();
        ResourceConfig config = new ResourceConfig();
        config.setNvidiaGPULoaderConfig(new NvidiaGPULoaderConfig(false));

        ResourceManager resourceManager = mock(ResourceManager.class);

        NvidiaGpuMetricsCollector collector =
                new NvidiaGpuMetricsCollector(metricRegistry, config, resourceManager, Duration.ofSeconds(30));

        collector.start();
        // Should not throw and should have no metrics registered
        assertTrue(metricRegistry.getGauges().isEmpty());
    }

    @Test
    void testStartDoesNothingWhenConfigNull() {
        MetricRegistry metricRegistry = new MetricRegistry();
        ResourceConfig config = new ResourceConfig();
        config.setNvidiaGPULoaderConfig(null);

        ResourceManager resourceManager = mock(ResourceManager.class);

        NvidiaGpuMetricsCollector collector =
                new NvidiaGpuMetricsCollector(metricRegistry, config, resourceManager, Duration.ofSeconds(30));

        collector.start();
        assertTrue(metricRegistry.getGauges().isEmpty());
    }

    @Test
    void testStartAndStopLifecycle() {
        MetricRegistry metricRegistry = new MetricRegistry();
        ResourceConfig config = new ResourceConfig();
        config.setNvidiaGPULoaderConfig(new NvidiaGPULoaderConfig(true));

        ResourceManager resourceManager = mock(ResourceManager.class);
        when(resourceManager.currentState()).thenReturn(new com.phonepe.drove.executor.resourcemgmt.ResourceInfo(
                null, null, new AvailableGPU(List.of(), List.of()), null));

        NvidiaGpuMetricsCollector collector =
                new NvidiaGpuMetricsCollector(metricRegistry, config, resourceManager, Duration.ofSeconds(30)) {
                    @Override
                    String queryNvidiaSmi() {
                        return """
                                <nvidia_smi_log>
                                  <gpu>
                                    <uuid>GPU-1</uuid>
                                    <utilization>
                                      <gpu_util>0 %</gpu_util>
                                      <memory_util>0 %</memory_util>
                                    </utilization>
                                    <fb_memory_usage>
                                      <total>1024 MiB</total>
                                      <used>0 MiB</used>
                                    </fb_memory_usage>
                                    <temperature>
                                      <gpu_temp>0 C</gpu_temp>
                                      <memory_temp>0 C</memory_temp>
                                    </temperature>
                                    <gpu_power_readings>
                                      <power_draw>0 W</power_draw>
                                    </gpu_power_readings>
                                    <clocks>
                                      <sm_clock>0 MHz</sm_clock>
                                    </clocks>
                                    <clocks_event_reasons>
                                      <clocks_event_reason_hw_thermal_slowdown>Not Active</clocks_event_reason_hw_thermal_slowdown>
                                      <clocks_event_reason_sw_thermal_slowdown>Not Active</clocks_event_reason_sw_thermal_slowdown>
                                    </clocks_event_reasons>
                                  </gpu>
                                </nvidia_smi_log>
                                """;
                    }
                };

        collector.start();
        // After start, metrics should be registered
        assertFalse(metricRegistry.getGauges().isEmpty());

        collector.stop();
        // After stop, gauges still exist but poller is stopped
        assertFalse(metricRegistry.getGauges().isEmpty());
    }

    @Test
    void testStopWhenNotRunning() {
        MetricRegistry metricRegistry = new MetricRegistry();
        ResourceConfig config = new ResourceConfig();
        config.setNvidiaGPULoaderConfig(new NvidiaGPULoaderConfig(true));

        ResourceManager resourceManager = mock(ResourceManager.class);

        NvidiaGpuMetricsCollector collector =
                new NvidiaGpuMetricsCollector(metricRegistry, config, resourceManager, Duration.ofSeconds(30));

        // Calling stop before start should not throw
        collector.stop();
        assertTrue(metricRegistry.getGauges().isEmpty());
    }
}
