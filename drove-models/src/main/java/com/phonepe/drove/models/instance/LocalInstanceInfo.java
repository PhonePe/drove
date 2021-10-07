package com.phonepe.drove.models.instance;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

import java.util.Map;

/**
 *
 */
@Value
@Jacksonized
@Builder
@AllArgsConstructor
public class LocalInstanceInfo {
    String hostname;
    Map<String, InstancePort> ports;
}
