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

package com.phonepe.drove.controller.engine;

import com.phonepe.drove.common.CommonUtils;
import io.dropwizard.util.Duration;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import net.jodah.failsafe.Failsafe;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static com.phonepe.drove.controller.config.ControllerOptions.DEFAULT;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 *
 */
@Slf4j
class ControllerRetrySpecFactoryTest {

    @Test
    @SneakyThrows
    void testJobRetrySpec() {
        val value = new AtomicBoolean(false);
        val options = DEFAULT
                .withJobRetryCount(2)
                .withJobRetryInterval(Duration.milliseconds(100));
        val retrySpec = new DefaultControllerRetrySpecFactory(options).jobRetrySpec();
        val policy = CommonUtils.<Boolean>policy(retrySpec, x -> !x);
        log.info("Start");
        val ctr = new AtomicInteger();
        Failsafe.with(policy)
                .onSuccess(e -> log.info("Success: {}", e))
                .onFailure(e -> log.info("Failure: {}", e))
                .onComplete(e -> log.info("Complete {}", e))
                .get(() -> {
                    log.info("Called");
                    ctr.incrementAndGet();
                    return value.get();
                });
        assertEquals(3, ctr.get()); //Num attempts = retry + 1
    }

}