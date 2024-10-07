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

package com.phonepe.drove.controller.metrics;

import com.codahale.metrics.Gauge;
import com.codahale.metrics.MetricRegistry;
import com.google.common.annotations.VisibleForTesting;
import com.phonepe.drove.common.coverageutils.IgnoreInJacocoGeneratedReport;
import io.dropwizard.setup.Environment;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * A registry for cluster level metrics
 */
@Singleton
@Slf4j
public class ClusterMetricsRegistry {

    public static class UpdateableGauge implements Gauge<Long> {
        private final AtomicLong value;

        private UpdateableGauge(Long initialValue) {
            this.value = new AtomicLong(initialValue);
        }

        @Override
        public Long getValue() {
            return value.get();
        }

        public void setValue(Long value) {
            this.value.set(value);
        }
    }

    private final Map<String, UpdateableGauge> gauges = new ConcurrentHashMap<>();
    private final MetricRegistry registry;

    @Inject
    @IgnoreInJacocoGeneratedReport
    @SuppressWarnings("unused")
    public ClusterMetricsRegistry(final Environment environment) {
        this(environment.metrics());
    }

    @VisibleForTesting
    public ClusterMetricsRegistry(final MetricRegistry registry) {
        this.registry = registry;
    }

    public void setGaugeValue(String name, long value) {
        this.gauges.computeIfAbsent(name,
                                    metricName -> {
                                        val gauge = new UpdateableGauge(value);
                                        val actualName = metricName(name);
                                        this.registry.register(actualName, gauge);
                                        log.info("Created metric: {}", actualName);
                                        return gauge;
                                    })
                .setValue(value);
    }

    public void markMeter(String name) {
        registry.meter(metricName(name)).mark();
    }

    private static String metricName(String name) {
        return "com.phonepe.drove." + name;
    }

}
