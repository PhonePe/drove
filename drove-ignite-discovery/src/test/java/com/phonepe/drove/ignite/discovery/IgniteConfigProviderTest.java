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

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import com.phonepe.drove.ignite.discovery.communication.DroveIgniteTcpCommunication;
import com.phonepe.drove.ignite.discovery.config.DroveIgniteConfig;
import lombok.SneakyThrows;
import lombok.val;
import org.apache.ignite.spi.discovery.tcp.DroveIgniteTcpDiscovery;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import uk.org.webcompere.systemstubs.environment.EnvironmentVariables;
import uk.org.webcompere.systemstubs.jupiter.SystemStub;
import uk.org.webcompere.systemstubs.jupiter.SystemStubsExtension;

import java.net.InetAddress;
import java.util.Map;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static com.phonepe.drove.ignite.discovery.DiscoveryTestUtils.createStubForSingleMemberDiscovery;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@WireMockTest(httpPort = 8878)
@ExtendWith(SystemStubsExtension.class)
class IgniteConfigProviderTest {

    private static final String DROVE_INSTANCE_ID = "instanceId";
    private static final String DROVE_APP_ID = "appId";

    @RegisterExtension
    static WireMockExtension controller1 = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort())
            .build();


    @SystemStub
    private EnvironmentVariables environmentVariables = new EnvironmentVariables(Map.of("DROVE_APP_INSTANCE_AUTH_TOKEN", "TestToken",
            "DROVE_INSTANCE_ID", DROVE_INSTANCE_ID,
            "DROVE_APP_ID", DROVE_APP_ID));

    @Test
    @SneakyThrows
    void testConfigProvider() {
        val config = DroveIgniteConfig.builder()
                .discoveryPortName("discovery")
                .droveEndpoint(endpoint())
                .useAppNameForDiscovery(false)
                .communicationPortName("comm")
                .build();
        createStubForSingleMemberDiscovery(controller1, config.getDiscoveryPortName(), config.getCommunicationPortName(),
                DROVE_INSTANCE_ID);
        val igniteConfig = new IgniteConfigProvider().provideIgniteConfiguration(config);
        assertEquals( "127.0.0.1," + DROVE_APP_ID + "," + DROVE_INSTANCE_ID,
                igniteConfig.getConsistentId());
        assertTrue(igniteConfig.getDiscoverySpi() instanceof DroveIgniteTcpDiscovery);
        assertTrue(igniteConfig.getCommunicationSpi() instanceof DroveIgniteTcpCommunication);
    }

    private static String endpoint() {
        return controller1.baseUrl();
    }
}
