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

package org.apache.ignite.spi.discovery.tcp;


import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import com.phonepe.drove.client.DroveClient;
import com.phonepe.drove.common.CommonTestUtils;
import com.phonepe.drove.ignite.discovery.DroveIgniteInstanceHelper;
import com.phonepe.drove.ignite.discovery.config.DroveIgniteConfig;
import lombok.SneakyThrows;
import lombok.val;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import uk.org.webcompere.systemstubs.environment.EnvironmentVariables;
import uk.org.webcompere.systemstubs.jupiter.SystemStub;
import uk.org.webcompere.systemstubs.jupiter.SystemStubsExtension;

import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.badRequest;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.ok;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static com.phonepe.drove.ignite.discovery.DiscoveryTestUtils.createStubForFailed;
import static com.phonepe.drove.ignite.discovery.DiscoveryTestUtils.createStubForNoPeer;
import static com.phonepe.drove.ignite.discovery.DiscoveryTestUtils.createStubForNoSuchPort;
import static com.phonepe.drove.ignite.discovery.DiscoveryTestUtils.createStubForSingleMemberDiscovery;

@WireMockTest
@ExtendWith(SystemStubsExtension.class)
class DrovePeerTrackerTest {


    @SystemStub
    private EnvironmentVariables environmentVariables = new EnvironmentVariables(Map.of("DROVE_APP_INSTANCE_AUTH_TOKEN", "TestToken"));


    @RegisterExtension
    static WireMockExtension controller1 = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort())
            .build();

    @RegisterExtension
    static WireMockExtension controller2 = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort())
            .build();

    private static final ObjectMapper mapper = new ObjectMapper();
    private static final String portName = "ignite";
    private DrovePeerTracker drovePeerTracker;

    @BeforeEach
    void leaderStub() {
        controller1.stubFor(get(DroveClient.PING_API).willReturn(ok()));
        controller2.stubFor(get(DroveClient.PING_API).willReturn(badRequest()));
        val igniteConfig = DroveIgniteConfig.builder()
                .droveEndpoint(endpoint())
                .useAppNameForDiscovery(false)
                .build();
        val droveIgniteInstanceHelper = new DroveIgniteInstanceHelper(igniteConfig);
        this.drovePeerTracker = new DrovePeerTracker(droveIgniteInstanceHelper, portName, mapper);
    }

    @AfterEach
    void reset() {
        controller1.resetAll();
        controller2.resetAll();
    }


    @Test
    @SneakyThrows
    void testFindPeers() {
        createStubForSingleMemberDiscovery(controller1, portName, "");
        CommonTestUtils.waitUntil(() -> drovePeerTracker.peers() != null);
        Assertions.assertEquals(1, drovePeerTracker.peers().size());
    }

    @Test
    @SneakyThrows
    void testPeerCallBadStatus() {
        createStubForNoPeer(controller1);
        Assertions.assertTrue(drovePeerTracker.peers().isEmpty());
    }

    @Test
    @SneakyThrows
    void testPeerCallFail() {
        createStubForFailed(controller1);
        Assertions.assertTrue(drovePeerTracker.peers().isEmpty());
    }

    @Test
    @SneakyThrows
    void testPeerCallWrongPortName() {
        createStubForNoSuchPort(controller1);
        Assertions.assertTrue(drovePeerTracker.peers().isEmpty());
    }

    private static String endpoint() {
        return controller1.baseUrl() + "," + controller2.baseUrl();
    }
}
