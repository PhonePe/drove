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

package com.phonepe.drove.client;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import com.phonepe.drove.client.transport.basic.DroveHttpNativeTransport;
import com.phonepe.drove.common.CommonTestUtils;
import lombok.SneakyThrows;
import lombok.val;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 *
 */
@WireMockTest
class DroveClientSingleEndpointOptimisationTest {
    @Test
    @SneakyThrows
    void success(final WireMockRuntimeInfo wmRuntimeInfo) {
        stubFor(get(DroveClient.PING_API)
                        .willReturn(ok()));
        val baseUrl = wmRuntimeInfo.getHttpBaseUrl();
        val config = droveConfig(baseUrl);
        try (val dc = new DroveClient(config,
                                      List.of(),
                                      new DroveHttpNativeTransport(config))) {
            CommonTestUtils.waitUntil(() -> dc.leader().isPresent());
            assertEquals(baseUrl, dc.leader().orElse(null));
        }
    }

    @Test
    @SneakyThrows
    void ignoreFail(final WireMockRuntimeInfo wmRuntimeInfo) {
        stubFor(get(DroveClient.PING_API)
                        .willReturn(badRequest()));
        val baseUrl = wmRuntimeInfo.getHttpBaseUrl();
        val config = droveConfig(baseUrl);
        try (val dc = new DroveClient(config,
                                      List.of(),
                                      new DroveHttpNativeTransport(config))) {
            CommonTestUtils.delay(Duration.ofSeconds(3));
            assertEquals(baseUrl, dc.leader().orElse(null));
        }
    }

    private static DroveClientConfig droveConfig(String baseUrl) {
        return new DroveClientConfig(List.of(baseUrl),
                                     Duration.ofSeconds(1),
                                     Duration.ofSeconds(1),
                                     Duration.ofSeconds(1));
    }
}
