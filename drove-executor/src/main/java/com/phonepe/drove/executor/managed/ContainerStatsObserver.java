package com.phonepe.drove.executor.managed;

import com.codahale.metrics.MetricRegistry;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.exception.NotFoundException;
import com.github.dockerjava.api.model.BlkioStatEntry;
import com.github.dockerjava.api.model.MemoryStatsConfig;
import com.github.dockerjava.api.model.StatisticNetworksConfig;
import com.github.dockerjava.api.model.Statistics;
import com.github.dockerjava.core.InvocationBuilder;
import com.google.inject.Inject;
import com.phonepe.drove.executor.engine.ApplicationInstanceEngine;
import com.phonepe.drove.executor.engine.DockerLabels;
import com.phonepe.drove.executor.metrics.AverageCpuUsageGauge;
import com.phonepe.drove.executor.metrics.LongGauge;
import com.phonepe.drove.executor.metrics.MemoryAllocatedGauge;
import com.phonepe.drove.executor.metrics.MemoryUsageGauge;
import com.phonepe.drove.executor.metrics.MemoryUsagePercentageGauge;
import com.phonepe.drove.executor.metrics.OverallCpuUsageGauge;
import com.phonepe.drove.executor.metrics.PerCoreCpuUsageGauge;
import com.phonepe.drove.executor.metrics.TimeDiffGauge;
import com.phonepe.drove.models.application.requirements.ResourceType;
import com.phonepe.drove.models.info.resources.allocation.CPUAllocation;
import com.phonepe.drove.models.instance.InstanceInfo;
import com.phonepe.drove.models.instance.InstanceState;
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
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.ToLongFunction;

import static com.codahale.metrics.MetricRegistry.name;
import static com.phonepe.drove.models.instance.InstanceState.ACTIVE_STATES;

/**
 *
 */
@Order(60)
@Slf4j
@Singleton
public class ContainerStatsObserver implements Managed {
    private static final String STATS_OBSERVER_HANDLER_NAME = "stats-observer";
    private static final String REPORTER_HANDLER_NAME = "timed-reporter";

    private final MetricRegistry metricRegistry;
    private final Map<String, InstanceData> instances = new ConcurrentHashMap<>();
    private final ApplicationInstanceEngine instanceEngine;
    private final ScheduledSignal statsChecker;
    private final DockerClient client;

    @Value
    private static class SignalData<T> {
        InstanceInfo instanceInfo;
        T data;
    }


    @Inject
    public ContainerStatsObserver(
            MetricRegistry metricRegistry,
            ApplicationInstanceEngine instanceEngine,
            DockerClient client) {
        this(metricRegistry, instanceEngine, client, Duration.ofSeconds(30));
    }

    public ContainerStatsObserver(
            MetricRegistry metricRegistry,
            ApplicationInstanceEngine instanceEngine,
            DockerClient client,
            Duration refreshDuration) {
        this.metricRegistry = metricRegistry;
        this.instanceEngine = instanceEngine;
        this.statsChecker = new ScheduledSignal(refreshDuration);
        this.client = client;
    }

    @Override
    public void start() throws Exception {
        instanceEngine.onStateChange()
                .connect(STATS_OBSERVER_HANDLER_NAME, instanceInfo -> {
                    if (instanceInfo.getState().equals(InstanceState.HEALTHY)) {
                        registerTrackedContainer(instanceInfo.getInstanceId(), instanceInfo);
                        log.info("Starting to track instance: {}", instanceInfo.getInstanceId());
                    }
                    else {
                        if (!ACTIVE_STATES.contains(instanceInfo.getState())) {
                            unregisterTrackedContainer(instanceInfo.getInstanceId());
                            log.info("Stopped tracking instance: {}", instanceInfo.getInstanceId());
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

    private void registerTrackedContainer(final String instanceId, final InstanceInfo instanceInfo) {
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
            val data = new InstanceData(instanceInfo, dockerId);
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
            return data;
        });
        reportStatsForInstance(instanceId);
    }

    private void unregisterTrackedContainer(final String instanceId) {
        instances.computeIfPresent(instanceId, (iid, oldValue) -> {
            metricRegistry.remove(metricName(oldValue, "nr_throttled"));
            metricRegistry.remove(metricName(oldValue, "cpu_cores_allocated"));
            metricRegistry.remove(metricName(oldValue, "cpu_percentage_per_core"));
            metricRegistry.remove(metricName(oldValue, "cpu_percentage_overall"));
            metricRegistry.remove(metricName(oldValue, "cpu_absolute_per_ms"));
            metricRegistry.remove(metricName(oldValue, "memory_usage"));
            metricRegistry.remove(metricName(oldValue, "memory_usage_percentage"));
            metricRegistry.remove(metricName(oldValue, "memory_allocated"));
            metricRegistry.remove(metricName(oldValue, "memory_usage_max"));
            metricRegistry.remove(metricName(oldValue, "memory_usage_limit"));
            metricRegistry.remove(metricName(oldValue, "network_tx_bytes"));
            metricRegistry.remove(metricName(oldValue, "network_tx_per_sec_bytes"));
            metricRegistry.remove(metricName(oldValue, "network_tx_errors"));
            metricRegistry.remove(metricName(oldValue, "network_tx_dropped"));
            metricRegistry.remove(metricName(oldValue, "network_rx_bytes"));
            metricRegistry.remove(metricName(oldValue, "network_rx_per_sec_bytes"));
            metricRegistry.remove(metricName(oldValue, "network_rx_errors"));
            metricRegistry.remove(metricName(oldValue, "network_rx_dropped"));
            metricRegistry.remove(metricName(oldValue, "block_io_write_bytes"));
            return null;
        });
    }

    private int allocatedCpus(InstanceInfo instanceInfo) {
        return instanceInfo.getResources()
                .stream()
                .filter(r -> r.getType().equals(ResourceType.CPU))
                .map(r -> (CPUAllocation) r)
                .mapToInt(c -> c.getCores().values().stream().mapToInt(Set::size).sum())
                .findFirst()
                .orElse(1);
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
            }
            catch (NotFoundException e) {
                log.warn("Looks like the container {}/{} being tracked is not available anymore. Time to disconnect..",
                         instanceData.getInstanceInfo().getAppId(), instanceId);
                unregisterTrackedContainer(instanceId);
                instances.remove(instanceId);
            }
            catch (RuntimeException e) {
                if(e.getCause() instanceof InterruptedException) {
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


    private <T> TimeDiffGauge<T> diffGauge(final String name, InstanceInfo instanceInfo, ToLongFunction<T> generator) {
        val metricName = metricName(instanceInfo, name);
        return metricRegistry.register(metricName,
                                       new TimeDiffGauge<T>() {
                                           @Override
                                           public void consume(T data) {
                                               setValue(generator.applyAsLong(data));
                                           }
                                       });
    }

    private <T> LongGauge<T> gauge(final String name, InstanceInfo instanceInfo, ToLongFunction<T> generator) {
        val metricName = metricName(instanceInfo, name);
        return metricRegistry.register(metricName,
                                       new LongGauge<T>() {
                                           @Override
                                           public void consume(T data) {
                                               setValue(generator.applyAsLong(data));
                                           }
                                       });
    }

    @Value
    private static class InstanceData {
        InstanceInfo instanceInfo;
        String dockerId;
        ConsumingSyncSignal<Statistics> dataReceived = new ConsumingSyncSignal<>();
        ConsumingSyncSignal<SignalData<MemoryStatsConfig>> memoryStatsReceived = new ConsumingSyncSignal<>();
        ConsumingSyncSignal<SignalData<Collection<StatisticNetworksConfig>>> networkStatsReceived = new ConsumingSyncSignal<>();
        ConsumingSyncSignal<SignalData<Collection<BlkioStatEntry>>> ioStatsReceived = new ConsumingSyncSignal<>();

        public InstanceData(InstanceInfo instanceInfo, String dockerId) {
            this.instanceInfo = instanceInfo;
            this.dockerId = dockerId;
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
            if(null != data.getBlkioStats()) {
                val ioData = Objects.requireNonNullElse(data.getBlkioStats().getIoServiceBytesRecursive(), List.<BlkioStatEntry>of());
                if(!ioData.isEmpty()) {
                ioStatsReceived.dispatch(new SignalData<>(instanceInfo,
                                                          ioData.stream()
                                                                  .filter(Objects::nonNull)
                                                                  .toList()));
                }
            }
        }
    }

    private static String metricName(final InstanceData instanceData, String name) {
        return metricName(instanceData.getInstanceInfo(), name);
    }

    private static String metricName(final InstanceInfo instanceInfo, String name) {
        return name("com",
                    "phonepe",
                    "drove",
                    "executor",
                    "applications",
                    instanceInfo.getAppName(),
                    "instance",
                    instanceInfo.getInstanceId(),
                    name);
    }
}
