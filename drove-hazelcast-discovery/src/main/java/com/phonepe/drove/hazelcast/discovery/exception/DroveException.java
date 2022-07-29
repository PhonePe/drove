package com.phonepe.drove.hazelcast.discovery.exception;

import lombok.EqualsAndHashCode;
import lombok.Value;

@Value
@EqualsAndHashCode(callSuper = true)
public class DroveException extends Exception {
    int status;
    String message;

    public DroveException(int status, String message) {
        super(message + " (http status: " + status + ")");
        this.status = status;
        this.message = message;
    }
}