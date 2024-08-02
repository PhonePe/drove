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

package com.phonepe.drove.executor.metrics;

import com.codahale.metrics.Gauge;
import io.appform.signals.signalhandlers.SignalConsumer;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.atomic.AtomicLong;

/**
 * A simple gauge to measure long values. The {@link SignalConsumer#consume(Object)} method needs to be overridden
 * to update the value
 */
@Slf4j
public abstract class LongGauge<R> implements Gauge<Long>, SignalConsumer<R> {
    private final AtomicLong value;

    protected LongGauge() {
        this.value = new AtomicLong(0);
    }

    @Override
    public Long getValue() {
        return value.get();
    }

    protected final void setValue(long currValue) {
        value.set(currValue);
    }
}
