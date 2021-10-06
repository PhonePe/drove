package com.phonepe.drove.models.instance;

import lombok.Value;

import java.util.Map;

/**
 *
 */
@Value
public class LocalInstanceInfo {
    String hostname;
    Map<String, InstancePort> ports;
}
