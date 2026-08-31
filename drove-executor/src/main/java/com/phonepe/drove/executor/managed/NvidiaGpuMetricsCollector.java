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

import com.codahale.metrics.Gauge;
import com.codahale.metrics.MetricFilter;
import com.codahale.metrics.MetricRegistry;
import com.google.common.base.Stopwatch;
import com.google.common.base.Strings;
import com.google.common.util.concurrent.AtomicDouble;
import com.phonepe.drove.executor.resourcemgmt.ResourceConfig;
import com.phonepe.drove.executor.resourcemgmt.ResourceManager;
import io.appform.signals.signals.ScheduledSignal;
import io.dropwizard.lifecycle.Managed;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.w3c.dom.Document;
import org.w3c.dom.Node;

import ru.vyarus.dropwizard.guice.module.installer.order.Order;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static com.phonepe.drove.executor.resourcemgmt.resourceloaders.CommandUtils.NVIDIA_SMI_QUERY_XML_COMMAND;
import static com.phonepe.drove.executor.resourcemgmt.resourceloaders.CommandUtils.isCommandNotFound;
import static com.phonepe.drove.executor.resourcemgmt.resourceloaders.CommandUtils.parseCommandOutput;
import static com.phonepe.drove.executor.resourcemgmt.resourceloaders.XmlParsingUtils.parseXml;

@Slf4j
@Singleton
@Order(59)
public class NvidiaGpuMetricsCollector implements Managed {

    static final String METRIC_PREFIX = "com.phonepe.drove.executor.gpu";
    private static final String POLLER_HANDLER = "nvidia-gpu-metrics-reporter";
    private static final Pattern NUMERIC_PATTERN = Pattern.compile("([-+]?\\d+(?:\\.\\d+)?)");
    private static final Pattern SANITIZE_PATTERN = Pattern.compile("[^a-zA-Z0-9_]");
    private static final String GPU_POWER_READINGS = "gpu_power_readings";
    private static final String UTILIZATION = "utilization";

    private final MetricRegistry metricRegistry;
    private final ResourceConfig resourceConfig;
    private final ResourceManager resourceManager;
    private final ScheduledSignal poller;
    private final Map<String, AtomicLong> longMetrics = new ConcurrentHashMap<>();
    private final Map<String, AtomicDouble> doubleMetrics = new ConcurrentHashMap<>();
    @SuppressWarnings("java:S3077")
    private volatile Snapshot latestSnapshot = new Snapshot(List.of());
    private volatile boolean running;

    @Inject
    public NvidiaGpuMetricsCollector(
            final MetricRegistry metricRegistry,
            final ResourceConfig resourceConfig,
            final ResourceManager resourceManager) {
        this(metricRegistry, resourceConfig, resourceManager, Duration.ofSeconds(30));
    }

    NvidiaGpuMetricsCollector(
            final MetricRegistry metricRegistry,
            final ResourceConfig resourceConfig,
            final ResourceManager resourceManager,
            final Duration refreshDuration) {
        this.metricRegistry = metricRegistry;
        this.resourceConfig = resourceConfig;
        this.resourceManager = resourceManager;
        this.poller = new ScheduledSignal(refreshDuration);
    }

    @Override
    public void start() {
        if (resourceConfig.getNvidiaGPULoaderConfig() == null
                || !resourceConfig.getNvidiaGPULoaderConfig().isEnabled()) {
            log.info("Skipping NVIDIA GPU metric collection because nvidia gpu loader is disabled");
            return;
        }
        running = true;
        poller.connect(POLLER_HANDLER, this::collect);
        collect(new Date());
    }

    @Override
    public void stop() {
        if (!running) {
            return;
        }
        poller.disconnect(POLLER_HANDLER);
        poller.close();
        running = false;
        latestSnapshot = new Snapshot(List.of());
        metricRegistry.removeMatching(MetricFilter.startsWith(METRIC_PREFIX));
        longMetrics.clear();
        doubleMetrics.clear();
    }

    void collect(final Date now) {
        val stopwatch = Stopwatch.createStarted();
        try {
            publish(parseSnapshot(queryNvidiaSmi()));
            setScrapeSuccess(true);
            updateLongMetric(METRIC_PREFIX + "_scrape_last_success_epoch_ms", System.currentTimeMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            setScrapeSuccess(false);
            log.info("Interrupted while collecting NVIDIA GPU metrics");
        } catch (IOException e) {
            setScrapeSuccess(false);
            if (isCommandNotFound(e)) {
                log.info("nvidia-smi command not found; skipping NVIDIA GPU metrics collection");
                return;
            }
            log.warn("Failed to collect NVIDIA GPU metrics: {}", e.getMessage());
        } catch (Exception e) {
            setScrapeSuccess(false);
            log.warn("Error parsing NVIDIA GPU metrics", e);
        } finally {
            updateLongMetric(METRIC_PREFIX + "_scrape_duration_ms", stopwatch.elapsed().toMillis());
        }
    }

    String queryNvidiaSmi() throws InterruptedException, IOException {
        return String.join("", parseCommandOutput(NVIDIA_SMI_QUERY_XML_COMMAND, false));
    }

    void publish(final Snapshot snapshot) {
        latestSnapshot = snapshot;
        val gpus = snapshot.gpus();
        updateLongMetric(METRIC_PREFIX + "_count", gpus.size());

        // Single-pass aggregation for all numeric fields
        val gpuUtil = new Aggregator<Double>();
        val memCtrlUtil = new Aggregator<Double>();
        val memUsed = new Aggregator<Long>();
        val memTotal = new Aggregator<Long>();
        val memTemp = new Aggregator<Double>();
        val smClock = new Aggregator<Double>();
        val thermal = new Aggregator<Long>();

        for (val gpu : gpus) {
            gpuUtil.add(gpu.gpuUtilizationPct());
            memCtrlUtil.add(gpu.memoryBandwidthUtilizationPct());
            memUsed.add(gpu.memoryUsedBytes());
            memTotal.add(gpu.memoryTotalBytes());
            memTemp.add(gpu.memoryTempC());
            smClock.add(gpu.smClockMhz());
            thermal.add(gpu.thermalThrottled());
        }

        updateDoubleMetric(METRIC_PREFIX + "_utilization_average_percentage", gpuUtil.averageOrZero());
        updateDoubleMetric(METRIC_PREFIX + "_memory_controller_utilization_average_percentage", memCtrlUtil.averageOrZero());
        updateLongMetric(METRIC_PREFIX + "_memory_used_total_bytes", memUsed.sumOrZero());
        updateLongMetric(METRIC_PREFIX + "_memory_total_total_bytes", memTotal.sumOrZero());
        updateDoubleMetric(METRIC_PREFIX + "_memory_temperature_average_celsius", memTemp.averageOrZero());
        updateDoubleMetric(METRIC_PREFIX + "_sm_clock_average_mhz", smClock.averageOrZero());
        updateLongMetric(METRIC_PREFIX + "_thermal_throttled_devices", thermal.sumOrZero());

        val state = resourceManager.currentState().getGpuInfo();
        if (state != null) {
            updateLongMetric(METRIC_PREFIX + "_free_count", state.getFreeGpus().size());
            updateLongMetric(METRIC_PREFIX + "_used_count", state.getUsedGpus().size());
        } else {
            updateLongMetric(METRIC_PREFIX + "_free_count", 0L);
            updateLongMetric(METRIC_PREFIX + "_used_count", 0L);
        }

        for (val gpu : gpus) {
            val prefix = METRIC_PREFIX + "_device_" + sanitizeDimension(gpu.gpuId()) + "_";
            updateDoubleMetric(prefix + "utilization_percentage", Objects.requireNonNullElse(gpu.gpuUtilizationPct(), 0D));
            updateDoubleMetric(prefix + "memory_controller_utilization_percentage", Objects.requireNonNullElse(gpu.memoryBandwidthUtilizationPct(), 0D));
            updateLongMetric(prefix + "memory_used_bytes", Objects.requireNonNullElse(gpu.memoryUsedBytes(), 0L));
            updateLongMetric(prefix + "memory_total_bytes", Objects.requireNonNullElse(gpu.memoryTotalBytes(), 0L));
            updateDoubleMetric(prefix + "memory_utilization_percentage", memoryUtilization(gpu));
            updateDoubleMetric(prefix + "temperature_celsius", Objects.requireNonNullElse(gpu.temperatureC(), 0D));
            updateDoubleMetric(prefix + "power_draw_watts", Objects.requireNonNullElse(gpu.powerDrawWatts(), 0D));
            updateDoubleMetric(prefix + "memory_temperature_celsius", Objects.requireNonNullElse(gpu.memoryTempC(), 0D));
            updateDoubleMetric(prefix + "sm_clock_mhz", Objects.requireNonNullElse(gpu.smClockMhz(), 0D));
            updateLongMetric(prefix + "thermal_throttled", Objects.requireNonNullElse(gpu.thermalThrottled(), 0L));
        }
    }

    // ------------------------------------------------------------------
    // XML parsing
    // ------------------------------------------------------------------

    static Snapshot parseSnapshot(final String xmlOutput) throws Exception {
        Document doc = parseXml(xmlOutput);
        val gpuNodes = doc.getElementsByTagName("gpu");
        val result = new ArrayList<GpuSample>(gpuNodes.getLength());
        for (int i = 0; i < gpuNodes.getLength(); i++) {
            val gpuChildren = indexChildren(gpuNodes.item(i));
            val gpuId = Objects.requireNonNullElse(textOf(gpuChildren.get("uuid")), "gpu_" + i);

            val util = indexChildren(gpuChildren.get(UTILIZATION));
            val fbMem = indexChildren(gpuChildren.get("fb_memory_usage"));
            val temp = indexChildren(gpuChildren.get("temperature"));
            val clocks = indexChildren(gpuChildren.get("clocks"));
            val power = indexChildren(gpuChildren.get(GPU_POWER_READINGS));
            val throttle = indexChildren(gpuChildren.get("clocks_event_reasons"));

            // power_draw with fallback to average_power_draw
            val powerDraw = Stream.of(textOf(power.get("power_draw")), textOf(power.get("average_power_draw")))
                    .filter(Objects::nonNull)
                    .findFirst()
                    .map(NvidiaGpuMetricsCollector::parseDouble)
                    .orElse(null);

            result.add(new GpuSample(
                    gpuId,
                    parseDouble(textOf(util.get("gpu_util"))),
                    parseDouble(textOf(util.get("memory_util"))),
                    parseSizeInBytes(textOf(fbMem.get("used"))),
                    parseSizeInBytes(textOf(fbMem.get("total"))),
                    parseDouble(textOf(temp.get("gpu_temp"))),
                    powerDraw,
                    parseDouble(textOf(temp.get("memory_temp"))),
                    parseDouble(textOf(clocks.get("sm_clock"))),
                    isThermallyThrottled(throttle)));
        }
        return new Snapshot(List.copyOf(result));
    }

    private static Long isThermallyThrottled(final Map<String, Node> throttleChildren) {
        if (throttleChildren.isEmpty()) {
            return null;
        }
        val hwThermal = textOf(throttleChildren.get("clocks_event_reason_hw_thermal_slowdown"));
        val swThermal = textOf(throttleChildren.get("clocks_event_reason_sw_thermal_slowdown"));
        return isActive(hwThermal) || isActive(swThermal) ? 1L : 0L;
    }

    private static boolean isActive(final String value) {
        return value != null && value.trim().equalsIgnoreCase("Active");
    }

    private static Map<String, Node> indexChildren(final Node parent) {
        if (parent == null) {
            return Map.of();
        }
        val children = parent.getChildNodes();
        val map = new HashMap<String, Node>(children.getLength());
        for (int i = 0; i < children.getLength(); i++) {
            val child = children.item(i);
            if (child != null && child.getNodeType() == Node.ELEMENT_NODE) {
                map.putIfAbsent(child.getNodeName().toLowerCase(Locale.ROOT), child);
            }
        }
        return map;
    }

    private static String textOf(final Node node) {
        return node == null ? null : node.getTextContent();
    }

    private static Double parseDouble(final String rawValue) {
        if (Strings.isNullOrEmpty(rawValue) || rawValue.trim().equalsIgnoreCase("N/A")) {
            return null;
        }
        val matcher = NUMERIC_PATTERN.matcher(rawValue);
        return matcher.find() ? Double.parseDouble(matcher.group(1)) : null;
    }

    private static Long parseSizeInBytes(final String rawValue) {
        val numeric = parseDouble(rawValue);
        if (numeric == null) {
            return null;
        }
        val normalized = rawValue.toLowerCase(Locale.ROOT);
        if (normalized.contains("gib") || normalized.contains("gb")) {
            return Math.round(numeric * 1024 * 1024 * 1024);
        }
        if (normalized.contains("mib") || normalized.contains("mb")) {
            return Math.round(numeric * 1024 * 1024);
        }
        if (normalized.contains("kib") || normalized.contains("kb")) {
            return Math.round(numeric * 1024);
        }
        return numeric.longValue();
    }

    private static double memoryUtilization(final GpuSample sample) {
        if (sample.memoryUsedBytes() == null || sample.memoryTotalBytes() == null || sample.memoryTotalBytes() <= 0) {
            return 0D;
        }
        return (sample.memoryUsedBytes() * 100D) / sample.memoryTotalBytes();
    }

    // ------------------------------------------------------------------
    // Metric helpers
    // ------------------------------------------------------------------

    private void setScrapeSuccess(final boolean success) {
        updateLongMetric(METRIC_PREFIX + "_scrape_success", success ? 1L : 0L);
    }

    private void updateLongMetric(final String metricName, final long value) {
        val holder = longMetrics.computeIfAbsent(metricName, key -> {
            val h = new AtomicLong(0L);
            metricRegistry.register(key, (Gauge<Long>) h::get);
            return h;
        });
        holder.set(value);
    }

    private void updateDoubleMetric(final String metricName, final double value) {
        val holder = doubleMetrics.computeIfAbsent(metricName, key -> {
            val h = new AtomicDouble(0D);
            metricRegistry.register(key, (Gauge<Double>) h::get);
            return h;
        });
        holder.set(value);
    }

    private static String sanitizeDimension(final String value) {
        return SANITIZE_PATTERN.matcher(Objects.requireNonNullElse(value, "unknown")).replaceAll("_");
    }


    Snapshot latestSnapshot() {
        return latestSnapshot;
    }

    record Snapshot(List<GpuSample> gpus) {
    }

    record GpuSample(String gpuId,
                     Double gpuUtilizationPct,
                     Double memoryBandwidthUtilizationPct,
                     Long memoryUsedBytes,
                     Long memoryTotalBytes,
                     Double temperatureC,
                     Double powerDrawWatts,
                     Double memoryTempC,
                     Double smClockMhz,
                     Long thermalThrottled) {
    }

    /** Lightweight helper for aggregating nullable numeric values. */
    static final class Aggregator<N extends Number> {
        private double sum;
        private int count;

        void add(N value) {
            if (value != null) {
                sum += value.doubleValue();
                count++;
            }
        }

        double averageOrZero() {
            return count > 0 ? sum / count : 0D;
        }

        long sumOrZero() {
            return Math.round(sum);
        }
    }
}
