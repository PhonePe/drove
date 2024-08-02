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

package com.phonepe.drove.executor.engine;

import com.codahale.metrics.SharedMetricRegistries;
import com.github.dockerjava.api.model.Frame;
import com.github.dockerjava.api.model.StreamType;
import com.phonepe.drove.executor.ExecutorTestingUtils;
import lombok.val;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.fail;

/**
 *
 */
class InstanceLogHandlerTest {

    @Test
    void testLogging() {
        try {
            val logHandler = new InstanceLogHandler(Collections.emptyMap(),
                                                    ExecutorTestingUtils.createExecutorAppInstanceInfo(ExecutorTestingUtils.testAppInstanceSpec("blah"), 8080),
                                                    SharedMetricRegistries.getOrCreate("test"));
            logHandler.onNext(new Frame(StreamType.STDOUT, null));
            IntStream.rangeClosed(1, 100)
                    .forEach(i -> logHandler.onNext(new Frame(StreamType.STDOUT,
                                                              ("Message " + i).getBytes(StandardCharsets.UTF_8))));
            IntStream.rangeClosed(1, 10)
                    .forEach(i -> logHandler.onNext(new Frame(StreamType.STDERR,
                                                              ("Message " + i).getBytes(StandardCharsets.UTF_8))));
        }
        catch (Exception e) {
            fail("Should not have filed with: " + e.getMessage());
        }
    }

}