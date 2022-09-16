package com.phonepe.drove.hazelcast.discovery;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import com.hazelcast.config.*;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import com.phonepe.drove.client.DroveClient;
import com.phonepe.drove.common.CommonTestUtils;
import lombok.SneakyThrows;
import lombok.val;
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

    private HazelcastInstance getHazelcastInstance(int port) {
        return getHazelcastInstance(port, "TestToken");
    }

    @SneakyThrows
    private HazelcastInstance getHazelcastInstance(int port, String token) {
        Config config = new Config();
        config.setProperty("hazelcast.discovery.enabled", "true");
        config.setProperty("hazelcast.discovery.public.ip.enabled", "true");
        config.setProperty("hazelcast.socket.client.bind.any", "true");
        config.setProperty("hazelcast.socket.bind.any", "false");
        NetworkConfig networkConfig = config.getNetworkConfig();
        networkConfig.getInterfaces().addInterface("127.0.0.1").setEnabled(true);
        networkConfig.setPort(port);
        JoinConfig joinConfig = networkConfig.getJoin();
        joinConfig.getTcpIpConfig().setEnabled(false);
        joinConfig.getMulticastConfig().setEnabled(false);
        joinConfig.getAwsConfig().setEnabled(false);
        DiscoveryConfig discoveryConfig = joinConfig.getDiscoveryConfig();
        DiscoveryStrategyConfig discoveryStrategyConfig =
                new DiscoveryStrategyConfig(new DroveDiscoveryStrategyFactory());
        discoveryStrategyConfig.addProperty("drove-endpoint", "http://127.0.0.1:8878,http://127.0.0.1:8878");
        discoveryStrategyConfig.addProperty("port-name", "hazelcast");
        System.setProperty(DroveDiscoveryStrategy.TOKEN_PROPERTY, token);
        discoveryConfig.addDiscoveryStrategyConfig(discoveryStrategyConfig);
        val node = Hazelcast.newHazelcastInstance(config);
        CommonTestUtils.waitUntil(() -> node.getCluster() != null);
        return node;
    }

}
