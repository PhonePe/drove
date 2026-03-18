package com.phonepe.drove.executor.managed;

import com.codahale.metrics.MetricRegistry;
import com.phonepe.drove.executor.resourcemgmt.NvidiaGPULoaderConfig;
import com.phonepe.drove.executor.resourcemgmt.ResourceConfig;
import com.phonepe.drove.executor.resourcemgmt.ResourceManager;
import com.phonepe.drove.models.info.resources.available.AvailableGPU;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
					</utilization>
					<fb_memory_usage>
					  <total>40536 MiB</total>
					  <used>10134 MiB</used>
					</fb_memory_usage>
					<temperature>
					  <gpu_temp>61 C</gpu_temp>
					</temperature>
					<gpu_power_readings>
					  <average_power_draw>210.5 W</average_power_draw>
					</gpu_power_readings>
				  </gpu>
				  <gpu>
					<uuid>GPU-2222-BBBB</uuid>
					<utilization>
					  <gpu_util>56 %</gpu_util>
					</utilization>
					<fb_memory_usage>
					  <total>40536 MiB</total>
					  <used>8100 MiB</used>
					</fb_memory_usage>
					<temperature>
					  <gpu_temp>59 C</gpu_temp>
					</temperature>
					<gpu_power_readings>
					  <power_draw>185.0 W</power_draw>
					</gpu_power_readings>
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
		assertEquals(185.0, snapshot.gpus().get(1).powerDrawWatts());
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
				new NvidiaGpuMetricsCollector.GpuSample("GPU-1111-AAAA", 70D, 100L, 1000L, 55D, 200D),
				new NvidiaGpuMetricsCollector.GpuSample("GPU-2222-BBBB", 30D, 200L, 2000L, 52D, 150D)
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
	}
}



