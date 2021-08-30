package com.phonepe.drove.common.messages.controller;

import com.phonepe.drove.internalmodels.ControllerMessageType;
import com.phonepe.drove.internalmodels.MessageHeader;
import com.phonepe.drove.models.instance.InstanceInfo;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.Value;

/**
 *
 */
@Value
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class InstanceStateReportMessage extends ControllerMessage {
    InstanceInfo instanceInfo;

    public InstanceStateReportMessage(MessageHeader header, InstanceInfo instanceInfo) {
        super(ControllerMessageType.INSTANCE_STATE_REPORT, header);
        this.instanceInfo = instanceInfo;
    }

    @Override
    public <T> T accept(ControllerMessageVisitor<T> visitor) {
        return visitor.visit(this);
    }
}
