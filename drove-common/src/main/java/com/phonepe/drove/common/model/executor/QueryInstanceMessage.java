package com.phonepe.drove.common.model.executor;

import com.phonepe.drove.common.model.ExecutorMessageType;
import com.phonepe.drove.common.model.MessageHeader;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.Value;

/**
 *
 */
@Value
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class QueryInstanceMessage extends ExecutorMessage {

    String instanceId;

    public QueryInstanceMessage(MessageHeader header, String instanceId) {
        super(ExecutorMessageType.QUERY_INFO, header);
        this.instanceId = instanceId;
    }

    @Override
    public <T> T accept(ExecutorMessageVisitor<T> visitor) {
        return visitor.visit(this);
    }
}
