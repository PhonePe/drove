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

import com.google.common.util.concurrent.AtomicDouble;
import com.phonepe.drove.executor.metrics.corecalc.PercentageBasedCPUUsageGaugeBase;
import lombok.extern.slf4j.Slf4j;

/**
 * Calculates CPU usage per code by overallPercentage/#cores
 */
@Slf4j
public class PerCoreCpuUsageGauge extends PercentageBasedCPUUsageGaugeBase {
    private final int numAllocatedCpus;
    private final AtomicDouble currPercentage = new AtomicDouble();

    public PerCoreCpuUsageGauge(int numAllocatedCpus) {
        super();
        this.numAllocatedCpus = numAllocatedCpus;
    }

    @Override
    public Double getValue() {
        return currPercentage.get();
    }

    @Override
    protected final void consumeOverallPercentage(double overallPercentage) {
        currPercentage.set(overallPercentage / numAllocatedCpus);
    }
}
