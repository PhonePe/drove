package com.phonepe.drove.common.messages.controller;

import com.phonepe.drove.internalmodels.ControllerMessageType;
import com.phonepe.drove.internalmodels.Message;
import com.phonepe.drove.internalmodels.MessageHeader;

/**
 *
 */
public abstract class ControllerMessage extends Message<ControllerMessageType> {
    protected ControllerMessage(ControllerMessageType type, MessageHeader header) {
        super(type, header);
    }

    public abstract <T> T accept(final ControllerMessageVisitor<T> visitor);

}
