package com.phonepe.drove.executor.engine;

import com.phonepe.drove.common.model.MessageResponse;
import com.phonepe.drove.common.model.controller.ControllerMessage;

/**
 *
 */
public interface ControllerMessageSender {
    MessageResponse sendMessage(final ControllerMessage message);
}
