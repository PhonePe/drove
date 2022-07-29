package com.phonepe.drove.hazelcast.discovery;

import com.hazelcast.config.properties.PropertyDefinition;
import com.hazelcast.config.properties.PropertyTypeConverter;
import com.hazelcast.config.properties.SimplePropertyDefinition;

public interface DroveDiscoveryConfiguration {

    PropertyDefinition DROVE_ENDPOINT = new SimplePropertyDefinition("drove-endpoint", PropertyTypeConverter.STRING);
    PropertyDefinition PORT_NAME = new SimplePropertyDefinition("port-name", PropertyTypeConverter.STRING);
}
