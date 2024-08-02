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

package com.phonepe.drove.executor.managed;

import com.phonepe.drove.common.model.MessageDeliveryStatus;
import com.phonepe.drove.common.model.MessageResponse;
import com.phonepe.drove.executor.AbstractExecutorEngineEnabledTestBase;
import com.phonepe.drove.executor.engine.ExecutorCommunicator;
import com.phonepe.drove.executor.engine.ExecutorMessageHandler;
import com.phonepe.drove.models.instance.InstanceInfo;
import com.phonepe.drove.models.instance.InstanceState;
import lombok.SneakyThrows;
import lombok.val;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 *
 */
class ExecutorInstanceStateChangeNotifierTest extends AbstractExecutorEngineEnabledTestBase {

    @Test
    @SneakyThrows
    void testStateChange() {
        val iin = new InstanceInfo("TEST_APP-1",
                                   "TEST_APP",
                                   "INS1",
                                   "E1",
                                   null,
                                   null,
                                   InstanceState.HEALTHY,
                                   null,
                                   null,
                                   null,
                                   null);
        val ctr = new AtomicInteger();
        val messageHandler = new ExecutorMessageHandler(applicationInstanceEngine, taskInstanceEngine, blacklistingManager);
        val scn = new ExecutorInstanceStateChangeNotifier(
                resourceDB,
                new ExecutorCommunicator(
                        message -> {
                                             ctr.incrementAndGet();
                                             if (ctr.get() > 1) {
                                                 return new MessageResponse(message.getHeader(),
                                                                            MessageDeliveryStatus.REJECTED);
                                             }
                                             return new MessageResponse(message.getHeader(),
                                                                        MessageDeliveryStatus.ACCEPTED);
                                         },
                                         messageHandler), applicationInstanceEngine, taskInstanceEngine);
        scn.start();
        applicationInstanceEngine.onStateChange().dispatch(iin);
        assertEquals(1, ctr.get());
        applicationInstanceEngine.onStateChange().dispatch(iin);
        assertEquals(2, ctr.get());
        scn.stop();
        applicationInstanceEngine.onStateChange().dispatch(iin);
        applicationInstanceEngine.onStateChange().dispatch(iin);
        assertEquals(2, ctr.get());
    }


}