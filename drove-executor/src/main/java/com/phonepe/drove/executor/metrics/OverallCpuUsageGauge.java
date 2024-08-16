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

import com.google.common.util.concurrent.AtomicDouble;
import com.phonepe.drove.executor.metrics.corecalc.PercentageBasedCPUUsageGaugeBase;
import lombok.extern.slf4j.Slf4j;

/**
 * Calculates according to <a href="https://docs.docker.com/engine/api/v1.43/#tag/Container/operation/ContainerExport">...</a>
 */
@Slf4j
public class OverallCpuUsageGauge extends PercentageBasedCPUUsageGaugeBase {
    private final AtomicDouble currPercentage = new AtomicDouble();

    public OverallCpuUsageGauge() {
        super();
    }

    @Override
    public Double getValue() {
        return currPercentage.get();
    }

    @Override
    protected final void consumeOverallPercentage(double overallPercentage) {
        currPercentage.set(overallPercentage);
    }
}
