package com.phonepe.drove.hazelcast.discovery;

import com.hazelcast.config.properties.PropertyDefinition;
import com.hazelcast.config.properties.PropertyTypeConverter;
import com.hazelcast.config.properties.SimplePropertyDefinition;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class DroveDiscoveryConfiguration {
    public static final PropertyDefinition DROVE_ENDPOINT = new SimplePropertyDefinition("drove-endpoint", PropertyTypeConverter.STRING);
    public static final PropertyDefinition PORT_NAME = new SimplePropertyDefinition("port-name", PropertyTypeConverter.STRING);
    public static final PropertyDefinition TRANSPORT = new SimplePropertyDefinition("transport", true, PropertyTypeConverter.STRING);
    public static final PropertyDefinition CLUSTER_BY_APP_NAME = new SimplePropertyDefinition("cluster-by-app-name", true, PropertyTypeConverter.BOOLEAN);
}
