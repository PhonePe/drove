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
