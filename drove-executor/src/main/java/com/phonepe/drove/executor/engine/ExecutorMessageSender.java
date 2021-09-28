package com.phonepe.drove.executor.engine;

import com.phonepe.drove.common.model.MessageResponse;
import com.phonepe.drove.common.model.controller.ControllerMessage;

/**
 *
 */
public interface ExecutorMessageSender {
    MessageResponse sendRemoteMessage(final ControllerMessage message);
}
