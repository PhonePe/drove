/*
 *  Copyright (c) 2022 Original Author(s), PhonePe India Pvt. Ltd.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

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
                                                                       DroveDiscoveryConfiguration.DROVE_ENDPOINT,
                                                                       DroveDiscoveryConfiguration.TRANSPORT,
                                                                       DroveDiscoveryConfiguration.CLUSTER_BY_APP_NAME);

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
