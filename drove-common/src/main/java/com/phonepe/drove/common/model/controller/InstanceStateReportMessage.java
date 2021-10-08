package com.phonepe.drove.common.model.controller;

import com.phonepe.drove.common.model.ControllerMessageType;
import com.phonepe.drove.common.model.ExecutorResourceSnapshot;
import com.phonepe.drove.common.model.MessageHeader;
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
    ExecutorResourceSnapshot resourceSnapshot;
    InstanceInfo instanceInfo;

    public InstanceStateReportMessage(
            MessageHeader header,
            ExecutorResourceSnapshot resourceSnapshot,
            InstanceInfo instanceInfo) {
        super(ControllerMessageType.INSTANCE_STATE_REPORT, header);
        this.resourceSnapshot = resourceSnapshot;
        this.instanceInfo = instanceInfo;
    }

    @Override
    public <T> T accept(ControllerMessageVisitor<T> visitor) {
        return visitor.visit(this);
    }
}
