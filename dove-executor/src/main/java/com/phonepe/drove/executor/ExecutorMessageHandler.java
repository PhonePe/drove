package com.phonepe.drove.executor;

import com.phonepe.drove.common.messages.executor.ExecutorMessageVisitor;
import com.phonepe.drove.common.messages.executor.QueryInstanceMessage;
import com.phonepe.drove.common.messages.executor.StartInstanceMessage;
import com.phonepe.drove.common.messages.executor.StopInstanceMessage;
import com.phonepe.drove.internalmodels.MessageDeliveryStatus;
import com.phonepe.drove.internalmodels.MessageResponse;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

/**
 *
 */
@Slf4j
public class ExecutorMessageHandler implements ExecutorMessageVisitor<MessageResponse> {
    private final InstanceEngine engine;

    public ExecutorMessageHandler(InstanceEngine engine) {
        this.engine = engine;
    }

    @Override
    public MessageResponse visit(StartInstanceMessage startInstanceMessage) {
        try {
            log.info("Starting instance");
            engine.startInstance(startInstanceMessage.getSpec());
            return new MessageResponse(startInstanceMessage.getHeader(), MessageDeliveryStatus.ACCEPTED);
        }
        catch (Exception e) {
            log.error("Could not start: ", e);
            return new MessageResponse(startInstanceMessage.getHeader(), MessageDeliveryStatus.FAILED);
        }
    }

    @Override
    public MessageResponse visit(StopInstanceMessage stopInstanceMessage) {
        engine.stopInstance(stopInstanceMessage.getInstanceId());
        return new MessageResponse(stopInstanceMessage.getHeader(), MessageDeliveryStatus.ACCEPTED);
    }

    @Override
    public MessageResponse visit(QueryInstanceMessage queryInstanceMessage) {
        val state = engine.currentState(queryInstanceMessage.getInstanceId()).orElse(null);
        if(null == state) {
            return new MessageResponse(queryInstanceMessage.getHeader(), MessageDeliveryStatus.FAILED);
        }
        //TODO::QUEUE UP MESSAGE FOR SENDING
        return new MessageResponse(queryInstanceMessage.getHeader(), MessageDeliveryStatus.ACCEPTED);
    }
}
