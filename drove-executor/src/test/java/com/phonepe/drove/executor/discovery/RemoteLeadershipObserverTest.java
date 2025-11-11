/*
 *  Copyright (c) 2025 Original Author(s), PhonePe India Pvt. Ltd.
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

package com.phonepe.drove.executor.discovery;

import com.github.tomakehurst.wiremock.client.MappingBuilder;
import com.github.tomakehurst.wiremock.http.Fault;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.phonepe.drove.auth.config.ClusterAuthenticationConfig;
import com.phonepe.drove.common.CommonTestUtils;
import com.phonepe.drove.executor.utils.ExecutorUtils;
import com.phonepe.drove.models.info.nodedata.NodeData;
import lombok.SneakyThrows;
import lombok.val;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.net.MalformedURLException;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.stream.Stream;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests {@link RemoteLeadershipObserver}
 */

class RemoteLeadershipObserverTest {
    @RegisterExtension
    static WireMockExtension controller1 = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort())
            .build();

    @RegisterExtension
    static WireMockExtension controller2 = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort())
            .build();


    @AfterEach
    void reset() {
        controller1.resetAll();
        controller2.resetAll();
    }

    @Test
    @SneakyThrows
    void success() {
        controller1.stubFor(get(RemoteLeadershipObserver.PING_API).willReturn(ok()));
        controller2.stubFor(get(RemoteLeadershipObserver.PING_API).willReturn(badRequest()));
        val client = buildClient();
        client.start();
        try {
            CommonTestUtils.waitUntil(() -> client.leader().isPresent());
            assertEquals(controller1.getPort(), client.leader().map(NodeData::getPort).orElseThrow());
        }
        finally {
            client.stop();
        }
    }

    @NotNull
    private static RemoteLeadershipObserver buildClient() throws MalformedURLException {
        return new RemoteLeadershipObserver(ControllerConfig.builder()
                                                    .endpoints(List.of(URI.create(controller1.baseUrl()).toURL(),
                                                                       URI.create(controller2.baseUrl()).toURL()))
                                                    .build(),
                                            ExecutorUtils.buildControllerClient(ClusterAuthenticationConfig.DEFAULT),
                                            Duration.ofMillis(500));
    }

    @ParameterizedTest
    @SneakyThrows
    @MethodSource("failureResponses")
    void failoverOnFailure(MappingBuilder failureResponse) {
        controller1.stubFor(failureResponse);
        controller2.stubFor(get(RemoteLeadershipObserver.PING_API).willReturn(ok()));
        val client = buildClient();
        client.start();
        try {
            CommonTestUtils.waitUntil(() -> client.leader().isPresent());
            assertEquals(controller2.getPort(), client.leader().map(NodeData::getPort).orElseThrow());
        }
        finally {
            client.stop();
        }
    }

    private static Stream<Arguments> failureResponses() {
        return Stream.of(
                Arguments.of(get(RemoteLeadershipObserver.PING_API).willReturn(badRequest())),
                Arguments.of(get(RemoteLeadershipObserver.PING_API).willReturn(serverError())),
                Arguments.of(get(RemoteLeadershipObserver.PING_API)
                                     .willReturn(aResponse().withFault(Fault.MALFORMED_RESPONSE_CHUNK))),
                Arguments.of(get(RemoteLeadershipObserver.PING_API)
                                     .willReturn(aResponse().withFixedDelay(5_000)))
                        );
    }
}