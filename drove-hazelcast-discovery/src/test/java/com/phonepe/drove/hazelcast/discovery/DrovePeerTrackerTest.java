package com.phonepe.drove.hazelcast.discovery;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import com.hazelcast.logging.Slf4jFactory;
import com.phonepe.drove.client.DroveClient;
import com.phonepe.drove.common.CommonTestUtils;
import lombok.SneakyThrows;
import lombok.val;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.phonepe.drove.hazelcast.discovery.DiscoveryTestUtils.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 *
 */
@WireMockTest
class DrovePeerTrackerTest {
    private static final String API_PATH = "/apis/v1/internal/instances";


    @BeforeEach
    void leaderStub() {
        stubFor(get(DroveClient.PING_API).willReturn(ok()));
    }

    @Test
    @SneakyThrows
    void testFindPeers(WireMockRuntimeInfo wm) {
        createStubForSingleMemberDiscovery();
        try (val dpt = new DrovePeerTracker(wm.getHttpBaseUrl(),
                                            "TestToken",
                                            "hazelcast",
                                            new Slf4jFactory().getLogger("test"),
                                            MAPPER)) {
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
                                            MAPPER)) {
            fail("Should have failed");
        }
        catch (IllegalArgumentException e) {
            return;
        }
        fail("Should have failed already");
    }

    @Test
    @SneakyThrows
    void testFindPeersNoLeader(WireMockRuntimeInfo wm) {
        stubFor(get(DroveClient.PING_API).willReturn(badRequest()));
        createStubForSingleMemberDiscovery();
        try (val dpt = new DrovePeerTracker(wm.getHttpBaseUrl(),
                                            "TestToken",
                                            "hazelcast",
                                            new Slf4jFactory().getLogger("test"),
                                            MAPPER)) {
            CommonTestUtils.delay(Duration.ofSeconds(2));
            assertTrue(dpt.peers().isEmpty());
        }
    }


    @Test
    @SneakyThrows
    void testPeerCallBadStatus(WireMockRuntimeInfo wm) {
        createStubForNoPeer();
        try (val dpt = new DrovePeerTracker(wm.getHttpBaseUrl(),
                                            "TestToken",
                                            "hazelcast",
                                            new Slf4jFactory().getLogger("test"),
                                            MAPPER)) {
            CommonTestUtils.delay(Duration.ofSeconds(2));
            assertTrue(dpt.peers().isEmpty());
        }
    }

    @Test
    @SneakyThrows
    void testPeerCallIOError(WireMockRuntimeInfo wm) {
        createStubIOErrorr();
        try (val dpt = new DrovePeerTracker(wm.getHttpBaseUrl(),
                                            "TestToken",
                                            "hazelcast",
                                            new Slf4jFactory().getLogger("test"),
                                            MAPPER)) {
            CommonTestUtils.delay(Duration.ofSeconds(2));
            assertTrue(dpt.peers().isEmpty());
        }
    }

    @Test
    @SneakyThrows
    void testPeerCallFail(WireMockRuntimeInfo wm) {
        createStubForFailed();
        try (val dpt = new DrovePeerTracker(wm.getHttpBaseUrl(),
                                            "TestToken",
                                            "hazelcast",
                                            new Slf4jFactory().getLogger("test"),
                                            MAPPER)) {
            CommonTestUtils.delay(Duration.ofSeconds(2));
            assertTrue(dpt.peers().isEmpty());
        }
    }

    @Test
    @SneakyThrows
    void testPeerCallWrongPortName(WireMockRuntimeInfo wm) {
        createStubForNoSuchPort();
        try (val dpt = new DrovePeerTracker(wm.getHttpBaseUrl(),
                                            "TestToken",
                                            "hazelcast",
                                            new Slf4jFactory().getLogger("test"),
                                            MAPPER)) {
            CommonTestUtils.delay(Duration.ofSeconds(2));
            assertTrue(dpt.peers().isEmpty());
        }
    }

    @Test
    @SneakyThrows
    void testPeerCallWrongHost(WireMockRuntimeInfo wm) {
        createStubForDNSFail();
        try (val dpt = new DrovePeerTracker(wm.getHttpBaseUrl(),
                                            "TestToken",
                                            "hazelcast",
                                            new Slf4jFactory().getLogger("test"),
                                            MAPPER)) {
            CommonTestUtils.delay(Duration.ofSeconds(2));
            assertTrue(dpt.peers().isEmpty());
        }
    }
}