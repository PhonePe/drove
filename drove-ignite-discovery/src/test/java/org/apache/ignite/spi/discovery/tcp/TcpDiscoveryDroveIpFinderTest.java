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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import com.phonepe.drove.ignite.discovery.DroveIgniteInstanceHelper;
import com.phonepe.drove.ignite.discovery.config.DroveIgniteConfig;
import lombok.val;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.mockito.Mockito;

import java.net.InetSocketAddress;
import java.util.List;
import java.util.Optional;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static com.phonepe.drove.ignite.discovery.DiscoveryTestUtils.createStubForSingleMemberDiscovery;
import static org.mockito.ArgumentMatchers.any;

@WireMockTest
class TcpDiscoveryDroveIpFinderTest {

    @RegisterExtension
    static WireMockExtension controller1 = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort())
            .build();

    private DroveIgniteInstanceHelper droveIgniteInstanceHelper;
    private ObjectMapper mapper;

    @BeforeEach
    void setup() {
        this.droveIgniteInstanceHelper = Mockito.mock(DroveIgniteInstanceHelper.class);
        this.mapper = Mockito.mock(ObjectMapper.class);
    }

    @Test
    void testTcpDiscoveryIpFinder() throws JsonProcessingException {
        createStubForSingleMemberDiscovery(controller1, 5050);
        val config = DroveIgniteConfig.builder()
                .discoveryPortName("discovery")
                .droveEndpoint(controller1.baseUrl())
                .useAppNameForDiscovery(false)
                .build();
        Mockito.doReturn(Optional.of(List.of(InetSocketAddress.createUnresolved("localhost", 1080))))
                .when(droveIgniteInstanceHelper).findCurrentInstances(any());
        val ipFinder = new TcpDiscoveryDroveIpFinder(droveIgniteInstanceHelper, config.getDiscoveryPortName(), mapper);
        val registeredAddresses = ipFinder.getRegisteredAddresses();
        Assertions.assertEquals(1, registeredAddresses.size());
    }
}
