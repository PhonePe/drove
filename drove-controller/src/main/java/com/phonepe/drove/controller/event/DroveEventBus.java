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

package com.phonepe.drove.controller.event;

import com.phonepe.drove.models.events.DroveEvent;
import io.appform.functionmetrics.MonitoredFunction;
import io.appform.signals.signals.ConsumingFireForgetSignal;

import javax.inject.Singleton;

/**
 *
 */
@Singleton
@SuppressWarnings("java:S3740") 
public class DroveEventBus {
    private final ConsumingFireForgetSignal<DroveEvent> eventGenerated = new ConsumingFireForgetSignal<>();

    @MonitoredFunction
    public final void publish(final DroveEvent event) {
        eventGenerated.dispatch(event);
    }

    public final ConsumingFireForgetSignal<DroveEvent> onNewEvent() {
        return eventGenerated;
    }
}
