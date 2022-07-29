package com.phonepe.drove.hazelcast.discovery;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import com.github.tomakehurst.wiremock.stubbing.Scenario;
import com.hazelcast.config.*;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import com.phonepe.drove.common.CommonTestUtils;
import com.phonepe.drove.models.api.ApiResponse;
import com.phonepe.drove.models.instance.InstanceInfo;
import com.phonepe.drove.models.instance.InstancePort;
import com.phonepe.drove.models.instance.LocalInstanceInfo;
import lombok.SneakyThrows;
import lombok.val;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@WireMockTest(httpPort = 8878)
class DiscoveryTests {

    private final ObjectMapper mapper = new ObjectMapper();

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

    private void createStubForSingleMemberDiscovery() throws JsonProcessingException {
        InstanceInfo instanceInfo = InstanceInfo.builder()
                .appId("1_0_0")
                .appName("test_app")
                .instanceId("instanceId")
                .localInfo(LocalInstanceInfo.builder()
                                   .hostname("127.0.0.1")
                                   .ports(Map.of("hazelcast", InstancePort.builder()
                                           .hostPort(5701)
                                           .build()))
                                   .build())
                .build();
        ApiResponse<List<InstanceInfo>> response = ApiResponse.success(List.of(instanceInfo));
        stubFor(get(urlEqualTo("/apis/v1/internal/instances"))
                        .withHeader("App-Instance-Authorization", equalTo("TestToken"))
                        .willReturn(aResponse()
                                            .withStatus(200)
                                            .withHeader("Content-Type", "application/json")
                                            .withBody(mapper.writeValueAsBytes(response))));
    }

    private void createStubForSingleMemberDiscoveryWrong() throws JsonProcessingException {
        stubFor(get(urlEqualTo("/apis/v1/internal/instances"))
                        .withHeader("App-Instance-Authorization", equalTo("WrongToken"))
                        .willReturn(aResponse()
                                            .withStatus(401)
                                            .withHeader("Content-Type", "application/json")
                                            .withBody(mapper.writeValueAsBytes(ApiResponse.failure("Authorization failure")))));
    }

    private void createStubForSingleMemberDiscoveryWithRetry() throws JsonProcessingException {
        stubFor(get(urlEqualTo("/apis/v1/internal/instances"))
                        .withHeader("App-Instance-Authorization", equalTo("TestToken"))
                        .inScenario("scenario")
                        .whenScenarioStateIs(Scenario.STARTED)
                        .willSetStateTo("passed")
                        .willReturn(aResponse()
                                            .withStatus(400)
                                            .withHeader("Content-Type", "application/json")
                                            .withBody(mapper.writeValueAsBytes("invalid state!!!"))));
        InstanceInfo instanceInfo = InstanceInfo.builder()
                .appId("1_0_0")
                .appName("test_app")
                .instanceId("instanceId")
                .localInfo(LocalInstanceInfo.builder()
                                   .hostname("127.0.0.1")
                                   .ports(Map.of("hazelcast", InstancePort.builder()
                                           .hostPort(5701)
                                           .build()))
                                   .build())
                .build();
        ApiResponse<List<InstanceInfo>> response = ApiResponse.success(List.of(instanceInfo));
        stubFor(get(urlEqualTo("/apis/v1/internal/instances"))
                        .withHeader("App-Instance-Authorization", equalTo("TestToken"))
                        .inScenario("scenario")
                        .whenScenarioStateIs("passed")
                        .willSetStateTo(Scenario.STARTED)
                        .willReturn(aResponse()
                                            .withStatus(200)
                                            .withHeader("Content-Type", "application/json")
                                            .withBody(mapper.writeValueAsBytes(response))));
    }

    private void createStubForMultipleMembers() throws JsonProcessingException {
        val instanceInfo = InstanceInfo.builder()
                .appId("1_0_0")
                .appName("test_app")
                .instanceId("instanceId1")
                .localInfo(LocalInstanceInfo.builder()
                                   .hostname("127.0.0.1")
                                   .ports(Map.of("hazelcast", InstancePort.builder()
                                           .hostPort(5701)
                                           .build()))
                                   .build())
                .build();
        val instanceInfo1 = InstanceInfo.builder()
                .appId("1_0_0")
                .appName("test_app")
                .instanceId("instanceId2")
                .localInfo(LocalInstanceInfo.builder()
                                   .hostname("127.0.0.1")
                                   .ports(Map.of("hazelcast", InstancePort.builder()
                                           .hostPort(5702)
                                           .build()))
                                   .build())
                .build();

        val response = ApiResponse.success(List.of(instanceInfo, instanceInfo1));
        stubFor(get(urlEqualTo("/apis/v1/internal/instances"))
                        .withHeader("App-Instance-Authorization", equalTo("TestToken"))
                        .willReturn(aResponse()
                                            .withStatus(200)
                                            .withHeader("Content-Type", "application/json")
                                            .withBody(mapper.writeValueAsBytes(response))));
    }
}
