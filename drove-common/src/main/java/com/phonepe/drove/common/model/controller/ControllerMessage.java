package com.phonepe.drove.common.model.controller;

import com.phonepe.drove.common.model.ControllerMessageType;
import com.phonepe.drove.common.model.Message;
import com.phonepe.drove.common.model.MessageHeader;

/**
 *
 */
public abstract class ControllerMessage extends Message<ControllerMessageType> {
    protected ControllerMessage(ControllerMessageType type, MessageHeader header) {
        super(type, header);
    }

    public abstract <T> T accept(final ControllerMessageVisitor<T> visitor);

}
