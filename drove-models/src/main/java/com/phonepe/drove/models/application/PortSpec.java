package com.phonepe.drove.models.application;

import lombok.Value;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotEmpty;

/**
 *
 */
@Value
public class PortSpec {
    @NotEmpty
    String name;

    @Min(0)
    @Max(65_535)
    int port;
}
