package com.phonepe.drove.hazelcast.discovery;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import com.hazelcast.core.HazelcastInstance;
import com.phonepe.drove.client.DroveClient;
import com.phonepe.drove.common.CommonTestUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.UnknownHostException;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.phonepe.drove.hazelcast.discovery.DiscoveryTestUtils.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@WireMockTest(httpPort = 8878)
class DiscoveryTests {

    @BeforeEach
    void leaderStub() {
        stubFor(get(DroveClient.PING_API)
                        .willReturn(ok()));
    }

    @Test
    void testSingleMemberDiscovery() throws IOException, InterruptedException {
        createStubForSingleMemberDiscovery();
        HazelcastInstance hazelcast = getHazelcastInstance(5701);
        assertTrue(hazelcast.getCluster().getMembers().size() > 0);
        hazelcast.shutdown();
    }

    @Test
    void testSingleMemberDiscoveryWrongToken() throws IOException, InterruptedException {
        createStubForSingleMemberDiscoveryWrong();
        HazelcastInstance hazelcast = getHazelcastInstance(5701, "WrongToken");
        assertTrue(hazelcast.getCluster().getMembers().size() > 0);
        hazelcast.shutdown();
    }

    @Test
    void testSingleMemberDiscoveryWithRetry() throws IOException, InterruptedException {
        createStubForSingleMemberDiscoveryWithRetry();
        HazelcastInstance hazelcast = getHazelcastInstance(5701);
        assertTrue(hazelcast.getCluster().getMembers().size() > 0);
        hazelcast.shutdown();
    }

    @Test
    void testMultiMemberDiscovery() throws UnknownHostException, InterruptedException, JsonProcessingException {
        createStubForMultipleMembers();

        HazelcastInstance hazelcast1 = getHazelcastInstance(5701);
        HazelcastInstance hazelcast2 = getHazelcastInstance(5702);
        System.out.println(hazelcast1.getCluster().getMembers().toString());
        System.out.println(hazelcast2.getCluster().getMembers().toString());
        CommonTestUtils.waitUntil(() -> hazelcast2.getCluster().getMembers().size() > 1);
        assertTrue(hazelcast2.getCluster().getMembers().size() > 0);
        assertEquals(2, hazelcast2.getCluster().getMembers().size());
        hazelcast1.shutdown();
        hazelcast2.shutdown();
    }

}
