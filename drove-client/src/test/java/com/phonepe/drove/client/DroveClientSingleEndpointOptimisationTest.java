package com.phonepe.drove.client;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
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
        try (val dc = new DroveClient(new DroveClientConfig(List.of(baseUrl),
                                                            Duration.ofSeconds(1),
                                                            Duration.ofSeconds(1),
                                                            Duration.ofSeconds(1)),
                                      List.of())) {
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
        try (val dc = new DroveClient(new DroveClientConfig(List.of(baseUrl),
                                                            Duration.ofSeconds(1),
                                                            Duration.ofSeconds(1),
                                                            Duration.ofSeconds(1)),
                                      List.of())) {
            CommonTestUtils.delay(Duration.ofSeconds(3));
            assertEquals(baseUrl, dc.leader().orElse(null));
        }
    }}
