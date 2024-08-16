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

package com.phonepe.drove.executor.metrics;

import com.codahale.metrics.Gauge;
import com.github.dockerjava.api.model.MemoryStatsConfig;
import com.phonepe.drove.executor.metrics.corecalc.MemoryUsageGaugeBase;

import java.util.concurrent.atomic.AtomicLong;

/**
 *
 */
public class MemoryUsageGauge  extends MemoryUsageGaugeBase implements Gauge<Long> {
    private final AtomicLong usage = new AtomicLong(0L);

    @Override
    public Long getValue() {
        return usage.longValue();
    }

    @Override
    protected void consumeUsage(long memUsage, MemoryStatsConfig mem) {
        usage.set(memUsage);
    }

}
