/*
 *  Copyright (c) 2022 Original Author(s), PhonePe India Pvt. Ltd.
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

import com.github.dockerjava.api.model.MemoryStatsConfig;
import com.github.dockerjava.api.model.Statistics;
import io.appform.signals.signalhandlers.SignalConsumer;
import lombok.val;

import java.util.Objects;

/**
 *
 */
public abstract class MemoryUsageGaugeBase implements SignalConsumer<Statistics> {

    @Override
    public void consume(Statistics data) {
        if (null == data) {
            return;
        }
        val mem = data.getMemoryStats();
        consumeUsage(Objects.requireNonNullElse(mem.getUsage(), 0L) - getCacheUsage(mem), mem);
    }

    protected abstract void consumeUsage(long memUsage, MemoryStatsConfig mem);

    private long getCacheUsage(final MemoryStatsConfig mem) {
        if(null != mem) {
            val stats = mem.getStats();
            if(null != stats) {
                return Objects.requireNonNullElse(stats.getCache(), 0L);
            }
        }
        return 0L;
    }

}
