/*
 *  Copyright (c) 2024 Original Author(s), PhonePe India Pvt. Ltd.
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

package com.phonepe.drove.ignite.discovery;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.module.paramnames.ParameterNamesModule;
import com.phonepe.drove.ignite.discovery.communication.DroveIgniteTcpCommunication;
import com.phonepe.drove.ignite.discovery.config.DroveIgniteConfig;
import com.phonepe.drove.models.instance.LocalInstanceInfo;
import lombok.NoArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.apache.ignite.configuration.IgniteConfiguration;
import org.apache.ignite.internal.IgniteNodeAttributes;
import org.apache.ignite.logger.slf4j.Slf4jLogger;
import org.apache.ignite.spi.discovery.tcp.DroveIgniteTcpDiscovery;

import java.util.Collections;
import java.util.Objects;

@NoArgsConstructor
@Slf4j
public class IgniteConfigProvider {

    private static final String CONSISTENT_ID_DELIMITER = ",";

    @SneakyThrows
    public IgniteConfiguration provideIgniteConfiguration(final DroveIgniteConfig droveIgniteConfig) {
        IgniteConfiguration igniteConfiguration = new IgniteConfiguration();
        val logger = new Slf4jLogger();
        igniteConfiguration.setGridLogger(logger);

        val objectMapper = createObjectMapper();
        val droveIgniteInstanceHelper = new DroveIgniteInstanceHelper(droveIgniteConfig);

        val localInstanceInfo = new LocalInstanceTracker(droveIgniteInstanceHelper, objectMapper)
                .getLocalInstanceInfo().orElse(null);
        Objects.requireNonNull(localInstanceInfo, "Drove LocalInstanceInfo cannot be null");

        igniteConfiguration.setConsistentId(getConsistentId(localInstanceInfo));
        igniteConfiguration.setDiscoverySpi(new DroveIgniteTcpDiscovery(droveIgniteConfig, localInstanceInfo,
                droveIgniteInstanceHelper, objectMapper));
        igniteConfiguration.setUserAttributes(Collections.singletonMap(IgniteNodeAttributes.ATTR_MACS_OVERRIDE,
                System.getenv("DROVE_INSTANCE_ID")));

        igniteConfiguration.setCommunicationSpi(new DroveIgniteTcpCommunication(droveIgniteConfig, localInstanceInfo));

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                droveIgniteInstanceHelper.close();
            }
            catch (Exception e) {
                log.error("Error adding shutdown hook!", e);
            }
        }));

        return igniteConfiguration;
    }

    private ObjectMapper createObjectMapper() {
        val objectMapper = new ObjectMapper();
        objectMapper.registerModule(new ParameterNamesModule());
        objectMapper.setSerializationInclusion(JsonInclude.Include.NON_EMPTY);
        objectMapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        objectMapper.disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);
        objectMapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        return objectMapper;
    }

    private String getConsistentId(final LocalInstanceInfo localInstanceInfo) {
        val instanceId = System.getenv("DROVE_INSTANCE_ID");
        val appId = System.getenv("DROVE_APP_ID");
        return localInstanceInfo.getHostname() + CONSISTENT_ID_DELIMITER + appId + CONSISTENT_ID_DELIMITER + instanceId;
    }
}
