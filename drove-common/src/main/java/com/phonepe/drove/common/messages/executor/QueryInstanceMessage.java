package com.phonepe.drove.common.messages.executor;

import com.phonepe.drove.internalmodels.ExecutorMessageType;
import com.phonepe.drove.internalmodels.MessageHeader;
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
