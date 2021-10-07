package com.phonepe.drove.models.instance;

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
}
