package com.phonepe.drove.hazelcast.discovery;

import com.hazelcast.config.properties.PropertyDefinition;
import com.hazelcast.logging.ILogger;
import com.hazelcast.spi.discovery.DiscoveryNode;
import com.hazelcast.spi.discovery.DiscoveryStrategy;
import com.hazelcast.spi.discovery.DiscoveryStrategyFactory;

import java.util.Collection;
import java.util.List;
import java.util.Map;

public class DroveDiscoveryStrategyFactory implements DiscoveryStrategyFactory {

    private static final List<PropertyDefinition> PROPERTIES = List.of(DroveDiscoveryConfiguration.PORT_NAME,
                                                                             DroveDiscoveryConfiguration.DROVE_ENDPOINT);

    @Override
    public Class<? extends DiscoveryStrategy> getDiscoveryStrategyType() {
        return DroveDiscoveryStrategy.class;
    }

    @Override
    public DiscoveryStrategy newDiscoveryStrategy(
            DiscoveryNode discoveryNode, ILogger logger, Map<String, Comparable> properties) {
        return new DroveDiscoveryStrategy(logger, properties);
    }

    @Override
    public Collection<PropertyDefinition> getConfigurationProperties() {
        return PROPERTIES;
    }
}
