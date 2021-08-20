package com.phonepe.drove.internalmodels;

import lombok.Value;

/**
 *
 */
@Value
public class MessageResponse {
    MessageHeader header;
    MessageDeliveryStatus status;
}
