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
import com.codahale.metrics.Gauge;
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
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import ru.vyarus.dropwizard.guice.module.installer.order.Order;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.IOException;
import java.io.StringReader;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.regex.Pattern;

import static com.phonepe.drove.executor.resourcemgmt.resourceloaders.CommandUtils.parseCommandOutput;

@Slf4j
@Singleton
@Order(61)
public class NvidiaGpuMetricsCollector implements Managed {

    static final String METRIC_PREFIX = "com.phonepe.drove.executor.gpu";
    private static final String POLLER_HANDLER = "nvidia-gpu-metrics-reporter";
    private static final Pattern NUMERIC_PATTERN = Pattern.compile("([-+]?\\d+(?:\\.\\d+)?)");

    private final MetricRegistry metricRegistry;
    private final ResourceConfig resourceConfig;
    private final ResourceManager resourceManager;
    private final ScheduledSignal poller;
    private final Map<String, AtomicLong> longMetrics = new ConcurrentHashMap<>();
    private final Map<String, AtomicDouble> doubleMetrics = new ConcurrentHashMap<>();
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
    }

    void collect(final Date now) {
        val startNanos = System.nanoTime();
        try {
            publish(parseSnapshot(queryNvidiaSmi()));
            updateLongMetric(metricName("scrape.success"), 1);
            updateLongMetric(metricName("scrape.last_success_epoch_ms"), System.currentTimeMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            updateLongMetric(metricName("scrape.success"), 0);
            log.info("Interrupted while collecting NVIDIA GPU metrics");
        } catch (IOException e) {
            updateLongMetric(metricName("scrape.success"), 0);
            if (e.getMessage() != null && e.getMessage().contains("error=2")) {
                log.info("nvidia-smi command not found; skipping NVIDIA GPU metrics collection");
                return;
            }
            log.warn("Failed to collect NVIDIA GPU metrics: {}", e.getMessage());
        } catch (Exception e) {
            updateLongMetric(metricName("scrape.success"), 0);
            log.warn("Error parsing NVIDIA GPU metrics", e);
        } finally {
            updateLongMetric(metricName("scrape.duration_ms"), Duration.ofNanos(System.nanoTime() - startNanos).toMillis());
        }
    }

    String queryNvidiaSmi() throws InterruptedException, IOException {
        return String.join("", parseCommandOutput("nvidia-smi -q -x", false));
    }

    void publish(final Snapshot snapshot) {
        latestSnapshot = snapshot;
        updateLongMetric(metricName("count"), snapshot.gpus().size());
        updateDoubleMetric(metricName("utilization.gpu.average_percentage"), average(snapshot.gpus(), GpuSample::gpuUtilizationPct));
        updateLongMetric(metricName("memory.used.total_bytes"), sum(snapshot.gpus(), GpuSample::memoryUsedBytes));
        updateLongMetric(metricName("memory.total.total_bytes"), sum(snapshot.gpus(), GpuSample::memoryTotalBytes));
        updateDoubleMetric(metricName("memory.temperature.average_celsius"), average(snapshot.gpus(), GpuSample::memoryTempC));
        updateDoubleMetric(metricName("sm_clock.average_mhz"), average(snapshot.gpus(), GpuSample::smClockMhz));
        updateDoubleMetric(metricName("memory.controller_utilization.average_percentage"), average(snapshot.gpus(), GpuSample::memoryControllerUtilizationPct));
        updateLongMetric(metricName("thermal.throttle_count"), count(snapshot.gpus(), GpuSample::thermalThrottled));

        val state = resourceManager.currentState().getGpuInfo();
        updateLongMetric(metricName("free_count"), state.getFreeGpus().size());
        updateLongMetric(metricName("used_count"), state.getUsedGpus().size());

        for (val gpu : snapshot.gpus()) {
            val gpuDimension = sanitizeDimension(gpu.gpuId());
            updateDoubleMetric(metricName("device", gpuDimension, "utilization.gpu_percentage"), orZero(gpu.gpuUtilizationPct()));
            updateLongMetric(metricName("device", gpuDimension, "memory.used_bytes"), orZero(gpu.memoryUsedBytes()));
            updateLongMetric(metricName("device", gpuDimension, "memory.total_bytes"), orZero(gpu.memoryTotalBytes()));
            updateDoubleMetric(metricName("device", gpuDimension, "memory.utilization_percentage"), memoryUtilization(gpu));
            updateDoubleMetric(metricName("device", gpuDimension, "temperature.celsius"), orZero(gpu.temperatureC()));
            updateDoubleMetric(metricName("device", gpuDimension, "power.draw_watts"), orZero(gpu.powerDrawWatts()));
            updateDoubleMetric(metricName("device", gpuDimension, "memory.temperature.celsius"), orZero(gpu.memoryTempC()));
            updateDoubleMetric(metricName("device", gpuDimension, "sm_clock.mhz"), orZero(gpu.smClockMhz()));
            updateDoubleMetric(metricName("device", gpuDimension, "memory.controller_utilization.percentage"), orZero(gpu.memoryControllerUtilizationPct()));
            updateLongMetric(metricName("device", gpuDimension, "thermal.throttled"), orZero(gpu.thermalThrottled()));
        }
    }

    static Snapshot parseSnapshot(final String xmlOutput) throws Exception {
        val factory = DocumentBuilderFactory.newInstance();
        factory.setValidating(false);
        factory.setNamespaceAware(true);
        factory.setFeature("http://xml.org/sax/features/namespaces", false);
        factory.setFeature("http://xml.org/sax/features/validation", false);
        factory.setFeature("http://apache.org/xml/features/nonvalidating/load-dtd-grammar", false);
        factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);

        val builder = factory.newDocumentBuilder();
        Document doc = builder.parse(new InputSource(new StringReader(xmlOutput)));
        val gpuNodes = doc.getElementsByTagName("gpu");
        val result = new ArrayList<GpuSample>();
        for (int i = 0; i < gpuNodes.getLength(); i++) {
            val gpuNode = gpuNodes.item(i);
            val gpuId = Objects.requireNonNullElse(findChildText(gpuNode, "uuid"), "gpu_" + i);
            val powerDraw = parseDouble(firstNonEmpty(
                    findNestedText(gpuNode, "gpu_power_readings", "instant_power_draw"),
                    firstNonEmpty(
                            findNestedText(gpuNode, "gpu_power_readings", "average_power_draw"),
                            findNestedText(gpuNode, "gpu_power_readings", "power_draw"))));
            result.add(new GpuSample(
                    gpuId,
                    parseDouble(findNestedText(gpuNode, "utilization", "gpu_util")),
                    parseDouble(findNestedText(gpuNode, "utilization", "memory_util")),
                    parseSizeInBytes(findNestedText(gpuNode, "fb_memory_usage", "used")),
                    parseSizeInBytes(findNestedText(gpuNode, "fb_memory_usage", "total")),
                    parseDouble(findNestedText(gpuNode, "temperature", "gpu_temp")),
                    powerDraw,
                    parseDouble(findNestedText(gpuNode, "temperature", "memory_temp")),
                    parseDouble(findNestedText(gpuNode, "clocks", "sm_clock")),
                    parseDouble(findNestedText(gpuNode, "utilization", "memory_util")),
                    isThermallyThrottled(gpuNode)));
        }
        return new Snapshot(List.copyOf(result));
    }

    private static Long isThermallyThrottled(final Node gpuNode) {
        val throttleReasons = findChild(gpuNode, "clocks_event_reasons");
        if (throttleReasons == null) {
            return null;
        }
        val hwThermal = findChildText(throttleReasons, "clocks_event_reason_hw_thermal_slowdown");
        val swThermal = findChildText(throttleReasons, "clocks_event_reason_sw_thermal_slowdown");
        if (isActive(hwThermal) || isActive(swThermal)) {
            return 1L;
        }
        return 0L;
    }

    private static boolean isActive(final String value) {
        return value != null && value.trim().equalsIgnoreCase("Active");
    }

    private static String firstNonEmpty(final String first, final String second) {
        return Strings.isNullOrEmpty(first) ? second : first;
    }

    private static String findNestedText(final Node parent, final String intermediateNodeName, final String childName) {
        val intermediateNode = findChild(parent, intermediateNodeName);
        if (intermediateNode == null) {
            return null;
        }
        return findChildText(intermediateNode, childName);
    }

    private static Node findChild(final Node parent, final String childName) {
        if (parent == null) {
            return null;
        }
        final NodeList childNodes = parent.getChildNodes();
        for (int i = 0; i < childNodes.getLength(); i++) {
            val child = childNodes.item(i);
            if (child != null && child.getNodeName().equalsIgnoreCase(childName)) {
                return child;
            }
        }
        return null;
    }

    private static String findChildText(final Node parent, final String childName) {
        val child = findChild(parent, childName);
        return child == null ? null : child.getTextContent();
    }

    private static Double parseDouble(final String rawValue) {
        if (Strings.isNullOrEmpty(rawValue) || rawValue.trim().equalsIgnoreCase("N/A")) {
            return null;
        }
        val matcher = NUMERIC_PATTERN.matcher(rawValue);
        if (!matcher.find()) {
            return null;
        }
        return Double.parseDouble(matcher.group(1));
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

    private static double average(final List<GpuSample> gpus, final Function<GpuSample, Double> extractor) {
        val nonNullValues = gpus.stream().map(extractor).filter(Objects::nonNull).toList();
        if (nonNullValues.isEmpty()) {
            return 0D;
        }
        return nonNullValues.stream().mapToDouble(Double::doubleValue).average().orElse(0D);
    }

    private static long sum(final List<GpuSample> gpus, final Function<GpuSample, Long> extractor) {
        return gpus.stream().map(extractor).filter(Objects::nonNull).mapToLong(Long::longValue).sum();
    }

    private static long count(final List<GpuSample> gpus, final Function<GpuSample, Long> extractor) {
        return gpus.stream().map(extractor).filter(Objects::nonNull).mapToLong(Long::longValue).sum();
    }

    private static double memoryUtilization(final GpuSample sample) {
        if (sample.memoryUsedBytes() == null || sample.memoryTotalBytes() == null || sample.memoryTotalBytes() <= 0) {
            return 0D;
        }
        return (sample.memoryUsedBytes() * 100D) / sample.memoryTotalBytes();
    }

    private void updateLongMetric(final String metricName, final long value) {
        val gauge = longMetrics.computeIfAbsent(metricName, key -> {
            val holder = new AtomicLong(0L);
            metricRegistry.register(key, (Gauge<Long>) holder::get);
            return holder;
        });
        gauge.set(value);
    }

    private void updateDoubleMetric(final String metricName, final double value) {
        val gauge = doubleMetrics.computeIfAbsent(metricName, key -> {
            val holder = new AtomicDouble(0D);
            metricRegistry.register(key, (Gauge<Double>) holder::get);
            return holder;
        });
        gauge.set(value);
    }

    private static long orZero(final Long value) {
        return value == null ? 0L : value;
    }

    private static double orZero(final Double value) {
        return value == null ? 0D : value;
    }

    private static String sanitizeDimension(final String value) {
        return Objects.requireNonNullElse(value, "unknown")
                .replaceAll("[^a-zA-Z0-9_]", "_");
    }

    private static String metricName(final String... parts) {
        val path = new ArrayList<String>();
        path.add(METRIC_PREFIX);
        path.addAll(List.of(parts));
        return String.join(".", path);
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
                     Double memoryControllerUtilizationPct,
                     Long thermalThrottled) {
    }
}
