package com.phonepe.drove.models.instance;

import com.phonepe.drove.models.application.PortType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

/**
 *
 */
@Value
@Jacksonized
@Builder
@AllArgsConstructor
public class InstancePort {
    int containerPort;
    int hostPort;
    PortType portType;
}
