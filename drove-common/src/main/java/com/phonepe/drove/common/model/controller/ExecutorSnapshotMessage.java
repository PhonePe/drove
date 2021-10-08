package com.phonepe.drove.common.model.controller;

import com.phonepe.drove.common.discovery.nodedata.ExecutorNodeData;
import com.phonepe.drove.common.model.ControllerMessageType;
import com.phonepe.drove.common.model.MessageHeader;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

/**
 *
 */
@Value
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class ExecutorSnapshotMessage extends ControllerMessage {
    ExecutorNodeData nodeData;

    @Jacksonized
    @Builder
    public ExecutorSnapshotMessage(MessageHeader header, ExecutorNodeData nodeData) {
        super(ControllerMessageType.EXECUTOR_SNAPSHOT, header);
        this.nodeData = nodeData;
    }

    @Override
    public <T> T accept(ControllerMessageVisitor<T> visitor) {
        return visitor.visit(this);
    }
}
