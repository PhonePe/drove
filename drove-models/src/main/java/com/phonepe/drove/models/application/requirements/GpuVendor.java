package com.phonepe.drove.models.application.requirements;

import com.fasterxml.jackson.annotation.JsonCreator;

import java.util.Locale;

public enum GpuVendor {
    NVIDIA,
    AMD,
    INTEL;

    @JsonCreator
    public static GpuVendor fromValue(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("GPU vendor must be provided");
        }
        return GpuVendor.valueOf(value.trim().toUpperCase());
    }
}

