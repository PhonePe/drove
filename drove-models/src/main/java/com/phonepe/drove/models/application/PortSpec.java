package com.phonepe.drove.models.application;

import lombok.Value;

import javax.validation.constraints.*;

/**
 *
 */
@Value
public class PortSpec {
    @NotEmpty(message = "- Specify a human readable name for the port." +
            " This will be used as key in health-check/readiness checks etc")
    @Pattern(regexp = "[a-zA-Z0-9\\-_]*", message = "- Only characters, numbers, hyphen and underscore is allowed")
    String name;

    @Min(value = 1, message = "- Port cannot be negative or 0")
    @Max(value = 65_535, message = "- Port cannot be more than 65K")
    int port;

    @NotNull(message = "- Please specify port type [HTTP/TCP/UDP]")
    PortType type;
}
