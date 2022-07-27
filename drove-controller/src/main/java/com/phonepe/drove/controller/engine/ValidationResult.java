package com.phonepe.drove.controller.engine;

import lombok.Value;

import java.util.List;
import java.util.Objects;

/**
 *
 */
@Value
public class ValidationResult {
    ValidationStatus status;
    List<String> messages;

    public static ValidationResult success() {
        return new ValidationResult(ValidationStatus.SUCCESS, List.of("Success"));
    }

    public static ValidationResult failure(final String message) {
        return failure(List.of(message));
    }

    public static ValidationResult failure(final List<String> messages) {
        Objects.requireNonNull(messages, "Validation failure message cannot be empty");
        return new ValidationResult(ValidationStatus.FAILURE, messages);
    }
}
