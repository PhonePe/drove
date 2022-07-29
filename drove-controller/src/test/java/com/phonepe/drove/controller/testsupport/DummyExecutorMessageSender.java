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
