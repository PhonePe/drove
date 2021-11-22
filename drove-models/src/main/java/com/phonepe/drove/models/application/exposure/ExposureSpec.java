package com.phonepe.drove.models.application.exposure;

import lombok.Value;

import javax.validation.constraints.NotEmpty;

/**
 *
 */
@Value
public class ExposureSpec {
    @NotEmpty
    String vhost;
    @NotEmpty
    String portName;
    ExposureMode mode;
}
