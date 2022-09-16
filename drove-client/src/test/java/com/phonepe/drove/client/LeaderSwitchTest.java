package com.phonepe.drove.client;

import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.phonepe.drove.common.CommonTestUtils;
import lombok.val;
import org.junit.AfterClass;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.IntStream;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 *
 */
public class LeaderSwitchTest {

    @RegisterExtension
    static WireMockExtension controller1 = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort())
            .build();

    @RegisterExtension
    static WireMockExtension controller2 = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort())
            .build();

    @Test
    void testSwitch() {
        val ctlr1FailState = stub(controller1, STARTED, 20, ok());

        controller1.stubFor(get(DroveClient.PING_API)
                                    .inScenario("LeaderShift")
                                    .whenScenarioStateIs(ctlr1FailState)
                                    .willReturn(badRequest())
                                    .willSetStateTo(ctlr1FailState));

        val ctlr2OkState = stub(controller2, STARTED, 30, badRequest());

        controller2.stubFor(get(DroveClient.PING_API)
                                    .inScenario("LeaderShift")
                                    .whenScenarioStateIs(ctlr2OkState)
                                    .willReturn(ok())
                                    .willSetStateTo(ctlr2OkState));

        val controller1Url = controller1.baseUrl();
        val controller2Url = controller2.baseUrl();
        val dc = new DroveClient(new DroveClientConfig(List.of(controller1Url, controller2Url),
                                                       Duration.ofMillis(100),
                                                       Duration.ofSeconds(1),
                                                       Duration.ofSeconds(1)),
                                 List.of());
        CommonTestUtils.waitUntil(() -> controller1Url.equals(dc.leader().orElse(null)));
        assertEquals(controller1Url, dc.leader().orElse(null));
        CommonTestUtils.waitUntil(() -> controller2Url.equals(dc.leader().orElse(null)));
        assertEquals(controller2Url, dc.leader().orElse(null));
    }

    @AfterClass
    public static void shutdown() {
        controller1.shutdownServer();
        controller2.shutdownServer();
    }

    private String stub(
            final WireMockExtension wm,
            final String startState,
            int threshold,
            ResponseDefinitionBuilder responseDefinitionBuilder) {
        val fromState = new AtomicReference<>(startState);
        IntStream.rangeClosed(1, threshold)
                .forEach(i -> wm.stubFor(get(DroveClient.PING_API)
                                                 .inScenario("LeaderShift")
                                                 .whenScenarioStateIs(fromState.getAndSet("GEN_STATE_" + i))
                                                 .willReturn(responseDefinitionBuilder)
                                                 .willSetStateTo(fromState.get())));
        return fromState.get();
    }
}
