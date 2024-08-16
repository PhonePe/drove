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

package com.phonepe.drove.executor.metrics.corecalc;

import com.codahale.metrics.Gauge;
import com.github.dockerjava.api.model.Statistics;
import io.appform.signals.signalhandlers.SignalConsumer;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import lombok.val;

import java.util.Collections;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/**
 *
 */
@RequiredArgsConstructor(access = AccessLevel.PROTECTED)
public abstract class PercentageBasedCPUUsageGaugeBase implements Gauge<Double>, SignalConsumer<Statistics> {

    private final AtomicReference<CPUUsage> currUsage = new AtomicReference<>(null);

    @Override
    public void consume(Statistics data) {
        if (null == data) {
            return;
        }
        val cpuStats = data.getCpuStats();
        if (null == cpuStats
                || null == cpuStats.getCpuUsage()
                || null == cpuStats.getCpuUsage().getTotalUsage()) {
            currUsage.set(new CPUUsage(0L, 0L));
            return;
        }
        val totalUsage = (long) cpuStats.getCpuUsage().getTotalUsage();
        val systemUsage = (long) Objects.requireNonNullElse(cpuStats.getSystemCpuUsage(), 0L);
        if (null != currUsage.get()) {
            val prev = currUsage.get();
            val cpuDelta = totalUsage - prev.getTotalUsage();
            val systemDelta = systemUsage - prev.getSystemUsage();
            if (cpuDelta > 0 || systemDelta > 0) {
                //Setting default value to 1 so that we get some numbers
                //Online cpu is null for both stats and prestats for podman
                // There the calculation is much simpler
                //Ref: https://github.com/containers/podman/blob/642a8f13a5f390a02c8ec05ec1b8557a0a1af2e9/libpod/stats_linux.go#L96
                val onlineCPUs = Objects.requireNonNullElse(cpuStats.getOnlineCpus(), 1L);
                //Looks like on cgroup v1, online cpus are not being returned properly
                //We take number of percpu_usage array elements and use that as an approximation
                val numPerCpuUsage = Objects.requireNonNullElse(
                        cpuStats.getCpuUsage().getPercpuUsage(), Collections.emptyList()).size();
                val cpuMultiplier = Math.max(onlineCPUs, numPerCpuUsage);
                val overallCPUUsagePercentage = ((double) cpuDelta * 100 * cpuMultiplier)/ systemDelta;
                consumeOverallPercentage(overallCPUUsagePercentage);
            }
        }
        currUsage.set(new CPUUsage(totalUsage, systemUsage));
    }

    protected abstract void consumeOverallPercentage(double overallPercentage);

    @Value
    private static class CPUUsage {
        long totalUsage;
        long systemUsage;
    }


}
