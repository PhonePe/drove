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

package com.phonepe.drove.controller.testsupport;

import com.phonepe.drove.common.model.ExecutorMessageType;
import com.phonepe.drove.common.model.MessageResponse;
import com.phonepe.drove.common.model.executor.ExecutorMessage;
import com.phonepe.drove.common.net.MessageSender;
import com.phonepe.drove.controller.testsupport.DummyExecutor;

import javax.inject.Inject;
import javax.inject.Singleton;

/**
 *
 */
@Singleton
public class DummyExecutorMessageSender implements MessageSender<ExecutorMessageType, ExecutorMessage> {
    private final DummyExecutor executor;

    @Inject
    private DummyExecutorMessageSender(DummyExecutor executor) {
        this.executor = executor;
    }

    @Override
    public MessageResponse send(ExecutorMessage message) {
        return executor.receiveMessage(message);
    }
}
