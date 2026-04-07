package com.phonepe.drove.models.application.requirements;

import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

import java.util.Objects;

@Value
@Jacksonized
@Builder
public class GpuType {
    GpuVendor vendor;
    String model;

    public GpuType(GpuVendor vendor, String model) {
        this.vendor = Objects.requireNonNull(vendor, "GPU vendor is mandatory");
        this.model = model;
    }
}

