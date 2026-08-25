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

package com.phonepe.drove.executor.discovery;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.badRequest;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.ok;
import static com.github.tomakehurst.wiremock.client.WireMock.serverError;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static com.phonepe.drove.common.CommonTestUtils.waitUntil;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.net.MalformedURLException;
import java.net.URI;
import java.util.List;

import com.github.tomakehurst.wiremock.http.Fault;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.phonepe.drove.auth.config.ClusterAuthenticationConfig;
import com.phonepe.drove.executor.utils.ExecutorUtils;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import lombok.SneakyThrows;
import lombok.val;

/**
 * Tests {@link ManagedLeadershipObserver} using {@link RemoteLeadershipObserver}
 */
class ManagedLeadershipObserverTest {

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
    void testObserverSuccess() {
        controller1.stubFor(get(RemoteLeadershipObserver.PING_API).willReturn(ok()));
        controller2.stubFor(get(RemoteLeadershipObserver.PING_API).willReturn(badRequest()));
        val observer = buildManagedObserver();
        observer.start();
        try {
            waitUntil(() -> observer.leader().isPresent());
            val leader = observer.leader().orElse(null);
            assertNotNull(leader);
            assertEquals(controller1.getPort(), leader.getPort());
        }
        finally {
            observer.stop();
        }
    }

    @Test
    @SneakyThrows
    void testObserverNoLeader() {
        controller1.stubFor(get(RemoteLeadershipObserver.PING_API).willReturn(badRequest()));
        controller2.stubFor(get(RemoteLeadershipObserver.PING_API).willReturn(serverError()));
        val observer = buildManagedObserver();
        observer.start();
        try {
            waitUntil(() -> observer.leader().isEmpty());
            val leader = observer.leader().orElse(null);
            assertEquals(null, leader);
        }
        finally {
            observer.stop();
        }
    }

    @Test
    @SneakyThrows
    void testObserverFailover() {
        controller1.stubFor(get(RemoteLeadershipObserver.PING_API)
                .willReturn(aResponse().withFault(Fault.MALFORMED_RESPONSE_CHUNK)));
        controller2.stubFor(get(RemoteLeadershipObserver.PING_API).willReturn(ok()));
        val observer = buildManagedObserver();
        observer.start();
        try {
            waitUntil(() -> observer.leader().isPresent());
            val leader = observer.leader().orElse(null);
            assertNotNull(leader);
            assertEquals(controller2.getPort(), leader.getPort());
        }
        finally {
            observer.stop();
        }
    }

    private ManagedLeadershipObserver buildManagedObserver() throws MalformedURLException {
        val remoteObserver = new RemoteLeadershipObserver(
                ControllerConfig.builder()
                        .endpoints(List.of(
                                URI.create(controller1.baseUrl()).toURL(),
                                URI.create(controller2.baseUrl()).toURL()))
                        .leadershipCheckInterval(io.dropwizard.util.Duration.milliseconds(500))
                        .build(),
                ExecutorUtils.buildControllerClient(ClusterAuthenticationConfig.DEFAULT));
        return new ManagedLeadershipObserver(remoteObserver);
    }
}
