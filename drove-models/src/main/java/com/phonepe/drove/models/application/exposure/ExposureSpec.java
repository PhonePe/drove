package com.phonepe.drove.models.application.exposure;

import lombok.Value;

import javax.validation.constraints.NotEmpty;

/**
 *
 */
@Value
public class ExposureSpec {
    @NotEmpty(message = "- Please provide virtual host name this app will be exposed as")
    String vhost;
    @NotEmpty(message = "- Name of the port that needs to be exposed")
    String portName;
    ExposureMode mode;
}
