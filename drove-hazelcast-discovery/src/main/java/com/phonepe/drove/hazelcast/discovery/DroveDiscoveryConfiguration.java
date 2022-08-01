package com.phonepe.drove.hazelcast.discovery;

import com.hazelcast.config.properties.PropertyDefinition;
import com.hazelcast.config.properties.PropertyTypeConverter;
import com.hazelcast.config.properties.SimplePropertyDefinition;
import lombok.experimental.UtilityClass;

@UtilityClass
public class DroveDiscoveryConfiguration {
    public static PropertyDefinition DROVE_ENDPOINT = new SimplePropertyDefinition("drove-endpoint", PropertyTypeConverter.STRING);
    public static PropertyDefinition PORT_NAME = new SimplePropertyDefinition("port-name", PropertyTypeConverter.STRING);
}
