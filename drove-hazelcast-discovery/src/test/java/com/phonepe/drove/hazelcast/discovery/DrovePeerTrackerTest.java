package com.phonepe.drove.hazelcast.discovery;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import com.hazelcast.logging.Slf4jFactory;
import com.phonepe.drove.client.DroveClient;
import com.phonepe.drove.common.CommonTestUtils;
import lombok.SneakyThrows;
import lombok.val;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.time.Duration;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static com.phonepe.drove.hazelcast.discovery.DiscoveryTestUtils.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 *
 */
@WireMockTest
class DrovePeerTrackerTest {
    private static final String API_PATH = "/apis/v1/internal/instances";
    @RegisterExtension
    static WireMockExtension controller1 = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort())
            .build();

    @RegisterExtension
    static WireMockExtension controller2 = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort())
            .build();

    @BeforeEach
    void leaderStub() {
        controller1.stubFor(get(DroveClient.PING_API).willReturn(ok()));
        controller2.stubFor(get(DroveClient.PING_API).willReturn(badRequest()));
    }

    @AfterEach
    void reset() {
        controller1.resetAll();
        controller2.resetAll();
    }

    @Test
    @SneakyThrows
    void testFindPeers() {
        createStubForSingleMemberDiscovery(controller1);
        try (val dpt = new DrovePeerTracker(endpoint(),
                                            "TestToken",
                                            "hazelcast",
                                            new Slf4jFactory().getLogger("test"),
                                            MAPPER,
                                            false,
                                            null)) {
            CommonTestUtils.waitUntil(() -> !dpt.peers().isEmpty());
            assertEquals(1, dpt.peers().size());
        }
    }

    @Test
    @SneakyThrows
    void testInvalidEndpoint() {
        try (val dpt = new DrovePeerTracker("",
                                            "TestToken",
                                            "hazelcast",
                                            new Slf4jFactory().getLogger("test"),
                                            MAPPER,
                                            false,
                                            null)) {
            fail("Should have failed");
        }
        catch (IllegalArgumentException e) {
            return;
        }
        fail("Should have failed already");
    }

    @Test
    @SneakyThrows
    void testFindPeersNoLeader() {
        stubFor(get(DroveClient.PING_API).willReturn(badRequest()));
        createStubForSingleMemberDiscovery();
        try (val dpt = new DrovePeerTracker(endpoint(),
                                            "TestToken",
                                            "hazelcast",
                                            new Slf4jFactory().getLogger("test"),
                                            MAPPER,
                                            false,
                                            null)) {
            CommonTestUtils.delay(Duration.ofSeconds(2));
            assertTrue(dpt.peers().isEmpty());
        }
    }


    @Test
    @SneakyThrows
    void testPeerCallBadStatus() {
        createStubForNoPeer(controller1);
        try (val dpt = new DrovePeerTracker(endpoint(),
                                            "TestToken",
                                            "hazelcast",
                                            new Slf4jFactory().getLogger("test"),
                                            MAPPER,
                                            false,
                                            null)) {
            CommonTestUtils.delay(Duration.ofSeconds(2));
            assertTrue(dpt.peers().isEmpty());
        }
    }

    @Test
    @SneakyThrows
    void testPeerCallIOError() {
        createStubIOErrorr(controller1);
        try (val dpt = new DrovePeerTracker(endpoint(),
                                            "TestToken",
                                            "hazelcast",
                                            new Slf4jFactory().getLogger("test"),
                                            MAPPER,
                                            false,
                                            null)) {
            CommonTestUtils.delay(Duration.ofSeconds(2));
            assertTrue(dpt.peers().isEmpty());
        }
    }

    @Test
    @SneakyThrows
    void testPeerCallFail() {
        createStubForFailed(controller1);
        try (val dpt = new DrovePeerTracker(endpoint(),
                                            "TestToken",
                                            "hazelcast",
                                            new Slf4jFactory().getLogger("test"),
                                            MAPPER,
                                            false,
                                            null)) {
            CommonTestUtils.delay(Duration.ofSeconds(2));
            assertTrue(dpt.peers().isEmpty());
        }
    }

    @Test
    @SneakyThrows
    void testPeerCallWrongPortName() {
        createStubForNoSuchPort(controller1);
        try (val dpt = new DrovePeerTracker(endpoint(),
                                            "TestToken",
                                            "hazelcast",
                                            new Slf4jFactory().getLogger("test"),
                                            MAPPER,
                                            false,
                                            null)) {
            CommonTestUtils.delay(Duration.ofSeconds(2));
            assertTrue(dpt.peers().isEmpty());
        }
    }

    @Test
    @SneakyThrows
    void testPeerCallWrongHost() {
        createStubForDNSFail(controller1);
        controller2.stubFor(get(DroveClient.PING_API).willReturn(badRequest()));
        try (val dpt = new DrovePeerTracker(endpoint(),
                                            "TestToken",
                                            "hazelcast",
                                            new Slf4jFactory().getLogger("test"),
                                            MAPPER,
                                            false,
                                            null)) {
            CommonTestUtils.delay(Duration.ofSeconds(2));
            assertTrue(dpt.peers().isEmpty());
        }
    }

    private static String endpoint() {
        return controller1.baseUrl() + "," + controller2.baseUrl();
    }
}