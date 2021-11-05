package com.phonepe.drove.controller.engine;

import com.phonepe.drove.common.model.MessageResponse;
import com.phonepe.drove.common.model.executor.ExecutorMessage;

/**
 *
 */
@FunctionalInterface
public interface ExecutorMessageSender {
    MessageResponse send(final ExecutorMessage message);
}
