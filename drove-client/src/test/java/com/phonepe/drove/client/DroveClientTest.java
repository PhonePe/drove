package com.phonepe.drove.client;

import com.github.tomakehurst.wiremock.http.Fault;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.phonepe.drove.client.transport.basic.DroveHttpNativeTransport;
import com.phonepe.drove.common.CommonTestUtils;
import lombok.SneakyThrows;
import lombok.val;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.time.Duration;
import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 *
 */
class DroveClientTest {

    @RegisterExtension
    static WireMockExtension controller1 = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort())
            .build();

    @RegisterExtension
    static WireMockExtension controller2 = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort())
            .build();

    @AfterEach
    void  reset() {
        controller1.resetAll();
        controller2.resetAll();
    }

    @Test
    @SneakyThrows
    void success() {
        controller1.stubFor(get(DroveClient.PING_API).willReturn(ok()));
        controller2.stubFor(get(DroveClient.PING_API).willReturn(badRequest()));
        val config = droveConfig();
        try (val dc = new DroveClient(config,
                                      List.of(),
                                      new DroveHttpNativeTransport(config))) {
            CommonTestUtils.waitUntil(() -> dc.leader().isPresent());
            assertEquals(controller1.baseUrl(), dc.leader().orElse(null));
        }
    }

    @Test
    @SneakyThrows
    void fail() {
        controller1.stubFor(get(DroveClient.PING_API).willReturn(serverError()));
        controller2.stubFor(get(DroveClient.PING_API).willReturn(badRequest()));
        val config = droveConfig();
        try (val dc = new DroveClient(config,
                                      List.of(),
                                      new DroveHttpNativeTransport(config))) {
            CommonTestUtils.delay(Duration.ofSeconds(3));
            assertNull(dc.leader().orElse(null));
        }
    }


    @Test
    @SneakyThrows
    void failBadRequest() {
        controller1.stubFor(get(DroveClient.PING_API).willReturn(badRequest()));
        controller2.stubFor(get(DroveClient.PING_API).willReturn(badRequest()));
        val config = droveConfig();
        try (val dc = new DroveClient(config,
                                      List.of(),
                                      new DroveHttpNativeTransport(config))) {
            CommonTestUtils.delay(Duration.ofSeconds(3));
            assertNull(dc.leader().orElse(null));
        }
    }

    @Test
    @SneakyThrows
    void failIOException() {
        controller1.stubFor(get(DroveClient.PING_API)
                        .willReturn(aResponse().withFault(Fault.MALFORMED_RESPONSE_CHUNK)));
        controller2.stubFor(get(DroveClient.PING_API).willReturn(badRequest()));
        val config = droveConfig();
        try (val dc = new DroveClient(config,
                                      List.of(),
                                      new DroveHttpNativeTransport(config))) {
            CommonTestUtils.delay(Duration.ofSeconds(3));
            assertNull(dc.leader().orElse(null));
        }
    }

    @Test
    @SneakyThrows
    void failTimeout() {
        controller1.stubFor(get(DroveClient.PING_API)
                        .willReturn(aResponse().withFixedDelay(5_000)));
        controller2.stubFor(get(DroveClient.PING_API).willReturn(badRequest()));
        val config = droveConfig();
        try (val dc = new DroveClient(config,
                                      List.of(),
                                      new DroveHttpNativeTransport(config))) {
            CommonTestUtils.delay(Duration.ofSeconds(3));
            assertNull(dc.leader().orElse(null));
        }
    }

    private static DroveClientConfig droveConfig() {
        return new DroveClientConfig(List.of(controller1.baseUrl(), controller2.baseUrl()),
                                     Duration.ofSeconds(1),
                                     Duration.ofSeconds(1),
                                     Duration.ofSeconds(1));
    }
}