/*
 *  Copyright (c) 2022 Original Author(s), PhonePe India Pvt. Ltd.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package com.phonepe.drove.common.model;

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
