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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import com.phonepe.drove.client.DroveClient;
import com.phonepe.drove.common.CommonTestUtils;
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
import static com.phonepe.drove.ignite.discovery.DiscoveryTestUtils.createStubForSingleMemberDiscoveryWithInstanceId;

@WireMockTest
@ExtendWith(SystemStubsExtension.class)
class LocalInstanceTrackerTest {

    @RegisterExtension
    static WireMockExtension controller1 = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort())
            .build();

    @RegisterExtension
    static WireMockExtension controller2 = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort())
            .build();

    private static final String INSTANCE_ID = "instanceId";

    @SystemStub
    private EnvironmentVariables environmentVariables = new EnvironmentVariables(Map.of("DROVE_INSTANCE_ID", INSTANCE_ID,
            "DROVE_APP_INSTANCE_AUTH_TOKEN", "TestToken"));

    private LocalInstanceTracker localInstanceTracker;

    private static final ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    void leaderStub() {
        controller1.stubFor(get(DroveClient.PING_API).willReturn(ok()));
        controller2.stubFor(get(DroveClient.PING_API).willReturn(badRequest()));
        val igniteConfig = DroveIgniteConfig.builder()
                .droveEndpoint(endpoint())
                .useAppNameForDiscovery(false)
                .build();
        val droveIgniteInstanceHelper = new DroveIgniteInstanceHelper(igniteConfig);
        this.localInstanceTracker = new LocalInstanceTracker(droveIgniteInstanceHelper, mapper);
    }

    @AfterEach
    void reset() {
        controller1.resetAll();
        controller2.resetAll();
    }

    @Test
    @SneakyThrows
    void testGetLocalInstance() {
        createStubForSingleMemberDiscoveryWithInstanceId(controller1, false, INSTANCE_ID);
        CommonTestUtils.waitUntil(() -> localInstanceTracker.getLocalInstanceInfo().isPresent());
        Assertions.assertTrue(localInstanceTracker.getLocalInstanceInfo().isPresent());
    }

    @Test
    @SneakyThrows
    void testGetLocalInstanceWhenInstanceNotFound() {
        createStubForSingleMemberDiscoveryWithInstanceId(controller1, false, INSTANCE_ID + "1");
        Assertions.assertTrue(localInstanceTracker.getLocalInstanceInfo().isEmpty());
    }

    @Test
    @SneakyThrows
    void testInstanceCallFail() {
        createStubForFailed(controller1);
        Assertions.assertTrue(localInstanceTracker.getLocalInstanceInfo().isEmpty());
    }

    @Test
    @SneakyThrows
    void testInstanceCallBadStatus() {
        createStubForNoPeer(controller1);
        Assertions.assertTrue(localInstanceTracker.getLocalInstanceInfo().isEmpty());
    }

    private static String endpoint() {
        return controller1.baseUrl() + "," + controller2.baseUrl();
    }
}
