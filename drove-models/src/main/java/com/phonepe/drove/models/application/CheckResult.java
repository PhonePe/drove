package com.phonepe.drove.models.application;

import lombok.Value;

/**
 *
 */
@Value
public class CheckResult {
    public enum Status {
        HEALTHY,
        UNHEALTHY,
        STOPPED
    }

    Status status;
    String message;

    public static CheckResult healthy() {
        return new CheckResult(Status.HEALTHY, "");
    }
    public static CheckResult unhealthy(String message) {
        return new CheckResult(Status.UNHEALTHY, message);
    }

    public static CheckResult stopped() {
        return new CheckResult(Status.STOPPED, "");
    }

}
