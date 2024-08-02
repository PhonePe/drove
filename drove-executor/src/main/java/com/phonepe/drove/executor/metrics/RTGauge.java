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
import com.github.dockerjava.api.model.Statistics;
import io.appform.signals.signalhandlers.SignalConsumer;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

import java.util.concurrent.atomic.AtomicReference;

/**
 *
 */
@Slf4j
public abstract class RTGauge<T> implements Gauge<T>, SignalConsumer<Statistics> {

    private final AtomicReference<T> value;
    private final T defaultValue;

    protected RTGauge(T defaultValue) {
        this.value = new AtomicReference<>(defaultValue);
        this.defaultValue = defaultValue;
    }

    @Override
    public T getValue() {
        return value.get();
    }

    protected abstract T extractValue(final Statistics stats);

    @Override
    public void consume(Statistics data) {
        try {
            val newValue = extractValue(data);
            value.set(newValue);
            log.info("Value is {}", value.get());
        }
        catch (Exception e) {
            log.error("Error extracting value", e);
        }
        value.set(defaultValue);
    }
}
