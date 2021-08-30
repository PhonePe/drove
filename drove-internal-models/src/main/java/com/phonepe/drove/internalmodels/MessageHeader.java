package com.phonepe.drove.internalmodels;

import lombok.Value;

import java.util.Date;
import java.util.UUID;

/**
 *
 */
@Value
public class MessageHeader {
    String id;
    SenderType senderType;
    Direction direction;
    String replyToId;
    Date messageTime;

    public static MessageHeader controllerRequest() {
        return new MessageHeader(UUID.randomUUID().toString(),
                                 SenderType.CONTROLLER,
                                 Direction.REQUEST,
                                 null,
                                 new Date());
    }

    public static MessageHeader controllerResponse(String replyToId) {
        return new MessageHeader(UUID.randomUUID().toString(),
                                 SenderType.CONTROLLER,
                                 Direction.REPLY,
                                 replyToId,
                                 new Date());
    }

    public static MessageHeader executorRequest() {
        return new MessageHeader(UUID.randomUUID().toString(),
                                 SenderType.EXECUTOR,
                                 Direction.REQUEST,
                                 null,
                                 new Date());
    }

    public static MessageHeader executorResponse(String replyToId) {
        return new MessageHeader(UUID.randomUUID().toString(),
                                 SenderType.EXECUTOR,
                                 Direction.REPLY,
                                 replyToId,
                                 new Date());
    }
}
