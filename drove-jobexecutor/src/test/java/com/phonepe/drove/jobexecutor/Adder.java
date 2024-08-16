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

package com.phonepe.drove.jobexecutor;

import lombok.SneakyThrows;

import java.security.SecureRandom;
import java.util.Date;
import java.util.Random;

/**
 *
 */
public final class Adder implements Job<Integer> {
    private static final Random RANDOM = new SecureRandom();
    private final int i;
    private final long currTime = new Date().getTime();

    Adder(int i) {
        this.i = i;
    }

    @Override
    public String jobId() {
        return "adder" + i + "-" + currTime;
    }

    @Override
    public void cancel() {

    }

    @SneakyThrows
    @Override
    @SuppressWarnings("java:S2925")
    public Integer execute(
            JobContext<Integer> context,
            final JobResponseCombiner<Integer> responseCombiner) {
        Thread.sleep(RANDOM.nextInt(100));
        return i;
    }
}
