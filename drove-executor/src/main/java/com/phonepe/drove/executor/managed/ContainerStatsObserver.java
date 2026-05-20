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

import com.codahale.metrics.MetricFilter;
import com.codahale.metrics.MetricRegistry;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.exception.NotFoundException;
import com.github.dockerjava.api.model.*;
import com.github.dockerjava.core.InvocationBuilder;
import com.google.inject.Inject;
import com.phonepe.drove.common.coverageutils.IgnoreInJacocoGeneratedReport;
import com.phonepe.drove.executor.engine.ApplicationInstanceEngine;
import com.phonepe.drove.executor.engine.DockerLabels;
import com.phonepe.drove.executor.engine.LocalServiceInstanceEngine;
import com.phonepe.drove.executor.metrics.*;
import com.phonepe.drove.models.application.requirements.ResourceType;
import com.phonepe.drove.models.info.resources.allocation.CPUAllocation;
import com.phonepe.drove.models.info.resources.allocation.GPUAllocation;
import com.phonepe.drove.models.info.resources.allocation.ResourceAllocation;
import com.phonepe.drove.models.instance.InstanceInfo;
import com.phonepe.drove.models.instance.InstanceState;
import com.phonepe.drove.models.instance.LocalServiceInstanceState;
import com.phonepe.drove.models.interfaces.DeployedInstanceInfo;
import com.phonepe.drove.models.interfaces.DeployedInstanceInfoVisitor;
import com.phonepe.drove.models.localservice.LocalServiceInstanceInfo;
import com.phonepe.drove.models.taskinstance.TaskInfo;
import io.appform.signals.signals.ConsumingSyncSignal;
import io.appform.signals.signals.ScheduledSignal;
import io.dropwizard.lifecycle.Managed;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import ru.vyarus.dropwizard.guice.module.installer.order.Order;

import javax.inject.Singleton;
import java.io.IOException;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.ToDoubleFunction;
import java.util.function.ToLongFunction;

import static com.codahale.metrics.MetricRegistry.name;
import static com.phonepe.drove.models.instance.InstanceState.ACTIVE_STATES;

/**
 *
 */
@Slf4j
@Singleton
@Order(60)
public class ContainerStatsObserver implements Managed {
    private static final String STATS_OBSERVER_HANDLER_NAME = "stats-observer";
    private static final String REPORTER_HANDLER_NAME = "timed-reporter";

    private final MetricRegistry metricRegistry;
    private final Map<String, InstanceData> instances = new ConcurrentHashMap<>();
    private final ApplicationInstanceEngine instanceEngine;
    private final LocalServiceInstanceEngine localServiceInstanceEngine;
    private final ScheduledSignal statsChecker;
    private final DockerClient client;
    private final NvidiaGpuMetricsCollector nvidiaGpuMetricsCollector;

    @Value
    private static class SignalData<T> {
        DeployedInstanceInfo instanceInfo;
        T data;
    }


    @Inject
    @IgnoreInJacocoGeneratedReport
    public ContainerStatsObserver(
            MetricRegistry metricRegistry,
            ApplicationInstanceEngine instanceEngine,
            LocalServiceInstanceEngine localServiceInstanceEngine,
            DockerClient client,
            NvidiaGpuMetricsCollector nvidiaGpuMetricsCollector) {
        this(metricRegistry,
             instanceEngine,
             localServiceInstanceEngine,
             client,
             nvidiaGpuMetricsCollector,
             Duration.ofSeconds(30));
    }

    public ContainerStatsObserver(
            MetricRegistry metricRegistry,
            ApplicationInstanceEngine instanceEngine,
            LocalServiceInstanceEngine localServiceInstanceEngine,
            DockerClient client,
            Duration refreshDuration) {
        this(metricRegistry, instanceEngine, localServiceInstanceEngine, client, null, refreshDuration);
    }

    public ContainerStatsObserver(
            MetricRegistry metricRegistry,
            ApplicationInstanceEngine instanceEngine,
            LocalServiceInstanceEngine localServiceInstanceEngine,
            DockerClient client,
            NvidiaGpuMetricsCollector nvidiaGpuMetricsCollector,
            Duration refreshDuration) {
        this.metricRegistry = metricRegistry;
        this.instanceEngine = instanceEngine;
        this.localServiceInstanceEngine = localServiceInstanceEngine;
        this.statsChecker = new ScheduledSignal(refreshDuration);
        this.client = client;
        this.nvidiaGpuMetricsCollector = nvidiaGpuMetricsCollector;
    }

    @Override
    public void start() throws Exception {
        instanceEngine.onStateChange()
                .connect(STATS_OBSERVER_HANDLER_NAME, instanceInfo -> {
                    if (instanceInfo.getState().equals(InstanceState.HEALTHY)) {
                        registerTrackedContainer(instanceInfo.getInstanceId(), instanceInfo);
                        log.info("Starting to track application instance: {}", instanceInfo.getInstanceId());
                    }
                    else {
                        if (!ACTIVE_STATES.contains(instanceInfo.getState())) {
                            unregisterTrackedContainer(instanceInfo.getInstanceId());
                            log.info("Stopped tracking application instance: {}", instanceInfo.getInstanceId());
                        }
                    }
                });
        localServiceInstanceEngine.onStateChange()
                .connect(STATS_OBSERVER_HANDLER_NAME, instanceInfo -> {
                    if (instanceInfo.getState().equals(LocalServiceInstanceState.HEALTHY)) {
                        registerTrackedContainer(instanceInfo.getInstanceId(), instanceInfo);
                        log.info("Starting to track local service instance: {}", instanceInfo.getInstanceId());
                    }
                    else {
                        if (!LocalServiceInstanceState.ACTIVE_STATES.contains(instanceInfo.getState())) {
                            unregisterTrackedContainer(instanceInfo.getInstanceId());
                            log.info("Stopped tracking local service instance: {}", instanceInfo.getInstanceId());
                        }
                    }
                });
        statsChecker.connect(REPORTER_HANDLER_NAME, this::reportStats);
    }

    @Override
    public void stop() throws Exception {
        statsChecker.disconnect(REPORTER_HANDLER_NAME);
        statsChecker.close();
        instanceEngine.onStateChange()
                .disconnect(STATS_OBSERVER_HANDLER_NAME);
        log.info("All handlers disconnected");
    }

    private void registerTrackedContainer(final String instanceId, final DeployedInstanceInfo instanceInfo) {
        instances.computeIfAbsent(instanceId, iid -> {
            val allocatedCpus = allocatedCpus(instanceInfo);
            val containers = client.listContainersCmd()
                    .withLabelFilter(Collections.singletonMap(DockerLabels.DROVE_INSTANCE_ID_LABEL, instanceId))
                    .exec();
            if (containers.isEmpty()) {
                log.warn("No container found for instance id {}", instanceId);
                return null;
            }
            val dockerId = containers.get(0).getId();
            log.info("Instance {} has docker id: {}", instanceId, dockerId);
            val data = new InstanceData(instanceInfo, dockerId, allocatedGpus(instanceInfo));
            data.getDataReceived()
                    .connect(gauge(
                            "nr_throttled",
                            instanceInfo,
                            statistics -> null != statistics
                                                  && null != statistics.getCpuStats()
                                                  && null != statistics.getCpuStats().getThrottlingData()
                                                  && null != statistics.getCpuStats()
                                    .getThrottlingData()
                                    .getThrottledPeriods()
                                          ? statistics.getCpuStats().getThrottlingData().getThrottledPeriods()
                                          : 0L))
                    .connect(metricRegistry.register(metricName(instanceInfo, "cpu_cores_allocated"),
                                                     new LongGauge<>() {
                                                         @Override
                                                         public void consume(Statistics data) {
                                                             setValue(allocatedCpus); //This is a fixed value
                                                         }
                                                     }))
                    .connect(metricRegistry.register(metricName(instanceInfo, "cpu_percentage_per_core"),
                                                     new PerCoreCpuUsageGauge(allocatedCpus)))
                    .connect(metricRegistry.register(metricName(instanceInfo, "cpu_percentage_overall"),
                                                     new OverallCpuUsageGauge()))
                    .connect(metricRegistry.register(metricName(instanceInfo, "cpu_absolute_per_ms"),
                                                     new AverageCpuUsageGauge()))
                    .connect(metricRegistry.register(metricName(instanceInfo, "memory_usage"),
                                                     new MemoryUsageGauge()))
                    .connect(metricRegistry.register(metricName(instanceInfo, "memory_usage_percentage"),
                                                     new MemoryUsagePercentageGauge()))
                    .connect(metricRegistry.register(metricName(instanceInfo, "memory_allocated"),
                                                     new MemoryAllocatedGauge()));

            data.getMemoryStatsReceived()
                    .connect(gauge("memory_usage_max",
                                   instanceInfo,
                                   mem -> Objects.requireNonNullElse(mem.getData().getMaxUsage(), 0L)))
                    .connect(gauge("memory_usage_limit",
                                   instanceInfo,
                                   mem -> Objects.requireNonNullElse(mem.getData().getLimit(), 0L)));
            data.getNetworkStatsReceived()
                    .connect(gauge("network_tx_bytes",
                                   instanceInfo,
                                   net -> net.getData()
                                           .stream()
                                           .filter(Objects::nonNull)
                                           .mapToLong(n -> Objects.requireNonNullElse(n.getTxBytes(), 0L))
                                           .sum()))
                    .connect(diffGauge("network_tx_per_sec_bytes",
                                       instanceInfo,
                                       net -> net.getData()
                                               .stream()
                                               .filter(Objects::nonNull)
                                               .mapToLong(n -> Objects.requireNonNullElse(n.getTxBytes(), 0L))
                                               .sum()))
                    .connect(gauge("network_tx_errors",
                                   instanceInfo,
                                   net -> net.getData()
                                           .stream()
                                           .filter(Objects::nonNull)
                                           .mapToLong(n -> Objects.requireNonNullElse(n.getTxErrors(), 0L))
                                           .sum()))
                    .connect(gauge("network_tx_dropped",
                                   instanceInfo,
                                   net -> net.getData()
                                           .stream()
                                           .filter(Objects::nonNull)
                                           .mapToLong(n -> Objects.requireNonNullElse(n.getTxDropped(), 0L))
                                           .sum()))
                    .connect(gauge("network_rx_bytes",
                                   instanceInfo,
                                   net -> net.getData()
                                           .stream()
                                           .filter(Objects::nonNull)
                                           .mapToLong(n -> Objects.requireNonNullElse(n.getRxBytes(), 0L))
                                           .sum()))
                    .connect(diffGauge("network_rx_per_sec_bytes",
                                       instanceInfo,
                                       net -> net.getData()
                                               .stream()
                                               .filter(Objects::nonNull)
                                               .mapToLong(n -> Objects.requireNonNullElse(n.getRxBytes(), 0L))
                                               .sum()))
                    .connect(gauge("network_rx_errors",
                                   instanceInfo,
                                   net -> net.getData()
                                           .stream()
                                           .filter(Objects::nonNull)
                                           .mapToLong(n -> Objects.requireNonNullElse(n.getRxErrors(), 0L))
                                           .sum()))
                    .connect(gauge("network_rx_dropped",
                                   instanceInfo,
                                   net -> net.getData()
                                           .stream()
                                           .filter(Objects::nonNull)
                                           .mapToLong(n -> Objects.requireNonNullElse(n.getRxDropped(), 0L))
                                           .sum()));
            data.getIoStatsReceived()
                    .connect(gauge("block_io_read_bytes",
                                   instanceInfo,
                                   ioData -> ioData.getData()
                                           .stream()
                                           .filter(entry -> entry.getOp().equals("read"))
                                           .mapToLong(entry -> entry.getMajor() + entry.getMinor())
                                           .sum()))
                    .connect(gauge("block_io_write_bytes",
                                   instanceInfo,
                                   ioData -> ioData.getData()
                                           .stream()
                                           .filter(entry -> entry.getOp().equals("write"))
                                           .mapToLong(entry -> entry.getMajor() + entry.getMinor())
                                           .sum()));
            data.getPidStatsReceived()
                    .connect(gauge("current_pid_usage", instanceInfo, SignalData::getData));
            data.getGpuStatsReceived()
                    .connect(gauge("gpu_devices_allocated", instanceInfo, gpu -> gpu.getData().getAllocatedDeviceCount()))
                    .connect(gauge("gpu_devices_observed", instanceInfo, gpu -> gpu.getData().getObservedDeviceCount()))
                    .connect(doubleGauge("gpu_utilization_percentage",
                                         instanceInfo,
                                         gpu -> gpu.getData().getUtilizationAvgPercentage()))
                    .connect(gauge("gpu_memory_used_bytes", instanceInfo, gpu -> gpu.getData().getMemoryUsedTotalBytes()))
                    .connect(gauge("gpu_memory_total_bytes", instanceInfo, gpu -> gpu.getData().getMemoryTotalTotalBytes()))
                    .connect(doubleGauge("gpu_memory_utilization_percentage",
                                         instanceInfo,
                                         gpu -> gpu.getData().getMemoryUtilizationAvgPercentage()))
                    .connect(doubleGauge("gpu_temperature_celsius",
                                         instanceInfo,
                                         gpu -> gpu.getData().getTemperatureAvgCelsius()))
                    .connect(doubleGauge("gpu_power_draw_watts",
                                         instanceInfo,
                                         gpu -> gpu.getData().getPowerDrawTotalWatts()))
                    .connect(doubleGauge("gpu_memory_temperature_celsius",
                                         instanceInfo,
                                         gpu -> gpu.getData().getMemoryTempAvgCelsius()))
                    .connect(doubleGauge("gpu_sm_clock_mhz",
                                         instanceInfo,
                                         gpu -> gpu.getData().getSmClockAvgMhz()))
                    .connect(doubleGauge("gpu_memory_controller_utilization_percentage",
                                         instanceInfo,
                                         gpu -> gpu.getData().getMemoryControllerUtilizationAvgPercentage()))
                    .connect(gauge("gpu_thermal_throttle_count",
                                   instanceInfo,
                                   gpu -> gpu.getData().getThermalThrottleCount()));
            return data;
        });
        reportStatsForInstance(instanceId);
    }

    private void unregisterTrackedContainer(final String instanceId) {
        instances.computeIfPresent(instanceId, (iid, oldValue) -> {
            metricRegistry.removeMatching(MetricFilter.contains(iid));
            return null;
        });
    }

    private int allocatedCpus(DeployedInstanceInfo instanceInfo) {
        return instanceInfo.accept(new DeployedInstanceInfoVisitor<List<ResourceAllocation>>() {
                    @Override
                    public List<ResourceAllocation> visit(InstanceInfo applicationInstanceInfo) {
                        return applicationInstanceInfo.getResources();
                    }

                    @Override
                    public List<ResourceAllocation> visit(TaskInfo taskInfo) {
                        return taskInfo.getResources();
                    }

                    @Override
                    public List<ResourceAllocation> visit(LocalServiceInstanceInfo localServiceInstanceInfo) {
                        return localServiceInstanceInfo.getResources();
                    }
                })
                .stream()
                .filter(r -> r.getType().equals(ResourceType.CPU))
                .map(r -> (CPUAllocation) r)
                .mapToInt(c -> c.getCores().values().stream().mapToInt(Set::size).sum())
                .findFirst()
                .orElse(1);
    }

    private Set<String> allocatedGpus(final DeployedInstanceInfo instanceInfo) {
        return instanceInfo.accept(new DeployedInstanceInfoVisitor<List<ResourceAllocation>>() {
                    @Override
                    public List<ResourceAllocation> visit(InstanceInfo applicationInstanceInfo) {
                        return applicationInstanceInfo.getResources();
                    }

                    @Override
                    public List<ResourceAllocation> visit(TaskInfo taskInfo) {
                        return taskInfo.getResources();
                    }

                    @Override
                    public List<ResourceAllocation> visit(LocalServiceInstanceInfo localServiceInstanceInfo) {
                        return localServiceInstanceInfo.getResources();
                    }
                })
                .stream()
                .filter(r -> r.getType().equals(ResourceType.GPU))
                .map(r -> (GPUAllocation) r)
                .map(GPUAllocation::getGpuIds)
                .filter(Objects::nonNull)
                .flatMap(Set::stream)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    private void reportStats(final Date now) {
        instances.keySet().forEach(this::reportStatsForInstance);
    }

    private void reportStatsForInstance(String instanceId) {
        instances.computeIfPresent(instanceId, (iid, instanceData) -> {
            val callback = new InvocationBuilder.AsyncResultCallback<Statistics>();
            client.statsCmd(instanceData.getDockerId()).exec(callback);
            try {
                instanceData.getDataReceived().dispatch(callback.awaitResult());
                if (!instanceData.getAllocatedGpuIds().isEmpty()) {
                    val snapshot = nvidiaGpuMetricsCollector == null
                            ? new NvidiaGpuMetricsCollector.Snapshot(List.of())
                            : nvidiaGpuMetricsCollector.latestSnapshot();
                    instanceData.getGpuStatsReceived().dispatch(new SignalData<>(
                            instanceData.getInstanceInfo(),
                            aggregateGpuMetrics(instanceData.getAllocatedGpuIds(), snapshot)));
                }
            }
            catch (NotFoundException e) {
                log.warn("Looks like the container {} being tracked is not available anymore. Time to disconnect..",
                         instanceData.getInstanceInfo().name());
                unregisterTrackedContainer(instanceId);
                instances.remove(instanceId);
            }
            catch (RuntimeException e) {
                if (e.getCause() instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                    log.info("Stats call interrupted");
                }
                else {
                    log.error("Error running ", e);
                }
            }
            finally {
                try {
                    callback.close();
                }
                catch (IOException e) {
                    log.error("Error closing callback: ", e);
                }
            }
            return instanceData;
        });
    }


    private <T> TimeDiffGauge<T> diffGauge(final String name, DeployedInstanceInfo instanceInfo, ToLongFunction<T> generator) {
        val metricName = metricName(instanceInfo, name);
        return metricRegistry.register(metricName,
                                       new TimeDiffGauge<T>() {
                                           @Override
                                           public void consume(T data) {
                                               setValue(generator.applyAsLong(data));
                                           }
                                       });
    }

    private <T> LongGauge<T> gauge(final String name, DeployedInstanceInfo instanceInfo, ToLongFunction<T> generator) {
        val metricName = metricName(instanceInfo, name);
        return metricRegistry.register(metricName,
                                       new LongGauge<T>() {
                                           @Override
                                           public void consume(T data) {
                                               setValue(generator.applyAsLong(data));
                                           }
                                       });
    }

    private <T> io.appform.signals.signalhandlers.SignalConsumer<T> doubleGauge(
            final String name,
            final DeployedInstanceInfo instanceInfo,
            final ToDoubleFunction<T> generator) {
        val metricName = metricName(instanceInfo, name);
        val gauge = new java.util.concurrent.atomic.AtomicReference<>(0D);
        metricRegistry.register(metricName, (com.codahale.metrics.Gauge<Double>) gauge::get);
        return data -> gauge.set(generator.applyAsDouble(data));
    }

    static InstanceGpuMetrics aggregateGpuMetrics(
            final Set<String> allocatedGpuIds,
            final NvidiaGpuMetricsCollector.Snapshot snapshot) {
        if (allocatedGpuIds == null || allocatedGpuIds.isEmpty()) {
            return InstanceGpuMetrics.ZERO;
        }
        val samples = snapshot.gpus()
                .stream()
                .filter(sample -> allocatedGpuIds.contains(sample.gpuId()))
                .toList();
        if (samples.isEmpty()) {
            return new InstanceGpuMetrics(allocatedGpuIds.size(), 0, 0D, 0L, 0L, 0D, 0D, 0D, 0D, 0D, 0D, 0L);
        }
        val memoryUtilAvg = samples.stream()
                .mapToDouble(sample -> {
                    if (sample.memoryUsedBytes() == null || sample.memoryTotalBytes() == null || sample.memoryTotalBytes() <= 0) {
                        return 0D;
                    }
                    return (sample.memoryUsedBytes() * 100D) / sample.memoryTotalBytes();
                })
                .average()
                .orElse(0D);
        return new InstanceGpuMetrics(
                allocatedGpuIds.size(),
                samples.size(),
                samples.stream().map(NvidiaGpuMetricsCollector.GpuSample::gpuUtilizationPct).filter(Objects::nonNull)
                        .mapToDouble(Double::doubleValue).average().orElse(0D),
                samples.stream().map(NvidiaGpuMetricsCollector.GpuSample::memoryUsedBytes).filter(Objects::nonNull)
                        .mapToLong(Long::longValue).sum(),
                samples.stream().map(NvidiaGpuMetricsCollector.GpuSample::memoryTotalBytes).filter(Objects::nonNull)
                        .mapToLong(Long::longValue).sum(),
                memoryUtilAvg,
                samples.stream().map(NvidiaGpuMetricsCollector.GpuSample::temperatureC).filter(Objects::nonNull)
                        .mapToDouble(Double::doubleValue).average().orElse(0D),
                samples.stream().map(NvidiaGpuMetricsCollector.GpuSample::powerDrawWatts).filter(Objects::nonNull)
                        .mapToDouble(Double::doubleValue).sum(),
                samples.stream().map(NvidiaGpuMetricsCollector.GpuSample::memoryTempC).filter(Objects::nonNull)
                        .mapToDouble(Double::doubleValue).average().orElse(0D),
                samples.stream().map(NvidiaGpuMetricsCollector.GpuSample::smClockMhz).filter(Objects::nonNull)
                        .mapToDouble(Double::doubleValue).average().orElse(0D),
                samples.stream().map(NvidiaGpuMetricsCollector.GpuSample::memoryControllerUtilizationPct).filter(Objects::nonNull)
                        .mapToDouble(Double::doubleValue).average().orElse(0D),
                samples.stream().map(NvidiaGpuMetricsCollector.GpuSample::thermalThrottled).filter(Objects::nonNull)
                        .mapToLong(Long::longValue).sum());
    }

    @Value
    private static class InstanceData {
        DeployedInstanceInfo instanceInfo;
        String dockerId;
        Set<String> allocatedGpuIds;
        ConsumingSyncSignal<Statistics> dataReceived = new ConsumingSyncSignal<>();
        ConsumingSyncSignal<SignalData<MemoryStatsConfig>> memoryStatsReceived = new ConsumingSyncSignal<>();
        ConsumingSyncSignal<SignalData<Collection<StatisticNetworksConfig>>> networkStatsReceived =
                new ConsumingSyncSignal<>();
        ConsumingSyncSignal<SignalData<Collection<BlkioStatEntry>>> ioStatsReceived = new ConsumingSyncSignal<>();
        ConsumingSyncSignal<SignalData<Long>> pidStatsReceived = new ConsumingSyncSignal<>();
        ConsumingSyncSignal<SignalData<InstanceGpuMetrics>> gpuStatsReceived = new ConsumingSyncSignal<>();

        public InstanceData(DeployedInstanceInfo instanceInfo, String dockerId, Set<String> allocatedGpuIds) {
            this.instanceInfo = instanceInfo;
            this.dockerId = dockerId;
            this.allocatedGpuIds = allocatedGpuIds;
            this.dataReceived.connect(this::handleStats);
        }

        private void handleStats(final Statistics data) {
            if (null == data) {
                return;
            }
            if (null != data.getMemoryStats()) {
                memoryStatsReceived.dispatch(new SignalData<>(instanceInfo, data.getMemoryStats()));
            }
            if (null != data.getNetworks()) {
                val networkData
                        = Objects.<Map<String, StatisticNetworksConfig>>requireNonNullElse(
                        data.getNetworks(), Collections.emptyMap());
                networkStatsReceived.dispatch(new SignalData<>(instanceInfo,
                                                               networkData
                                                                       .values()
                                                                       .stream()
                                                                       .filter(Objects::nonNull)
                                                                       .toList()));
            }
            if (null != data.getBlkioStats()) {
                val ioData = Objects.requireNonNullElse(data.getBlkioStats().getIoServiceBytesRecursive(),
                                                        List.<BlkioStatEntry>of());
                if (!ioData.isEmpty()) {
                    ioStatsReceived.dispatch(new SignalData<>(instanceInfo,
                                                              ioData.stream()
                                                                      .filter(Objects::nonNull)
                                                                      .toList()));
                }
            }
            if (null != data.getPidsStats()) {
                val pidCount = Objects.requireNonNullElse(data.getPidsStats().getCurrent(), -1L);
                if(pidCount > 0) {
                    pidStatsReceived.dispatch(new SignalData<>(instanceInfo, pidCount));
                }
            }
        }
    }

    @Value
    static class InstanceGpuMetrics {
        static final InstanceGpuMetrics ZERO = new InstanceGpuMetrics(0, 0, 0D, 0L, 0L, 0D, 0D, 0D, 0D, 0D, 0D, 0L);
        long allocatedDeviceCount;
        long observedDeviceCount;
        double utilizationAvgPercentage;
        long memoryUsedTotalBytes;
        long memoryTotalTotalBytes;
        double memoryUtilizationAvgPercentage;
        double temperatureAvgCelsius;
        double powerDrawTotalWatts;
        double memoryTempAvgCelsius;
        double smClockAvgMhz;
        double memoryControllerUtilizationAvgPercentage;
        long thermalThrottleCount;
    }

    private static String metricName(final DeployedInstanceInfo instanceInfo, String name) {
        return instanceInfo.accept(new DeployedInstanceInfoVisitor<>() {
            @Override
            public String visit(InstanceInfo applicationInstanceInfo) {
                return name("com",
                            "phonepe",
                            "drove",
                            "executor",
                            "applications",
                            applicationInstanceInfo.getAppName(),
                            "instance",
                            applicationInstanceInfo.getInstanceId(),
                            name);
            }

            @Override
            public String visit(TaskInfo taskInfo) {
                return "";
            }

            @Override
            public String visit(LocalServiceInstanceInfo localServiceInstanceInfo) {
                return name("com",
                            "phonepe",
                            "drove",
                            "executor",
                            "localservices",
                            localServiceInstanceInfo.getServiceName(),
                            "instance",
                            localServiceInstanceInfo.getInstanceId(),
                            name);
            }
        });

    }
}
