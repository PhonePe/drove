package com.phonepe.drove.common.model;

import lombok.Value;

/**
 *
 */
@Value
public class MessageResponse {
    MessageHeader header;
    MessageDeliveryStatus status;
}
