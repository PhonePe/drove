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

package com.phonepe.drove.hazelcast.discovery;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.module.paramnames.ParameterNamesModule;
import com.github.tomakehurst.wiremock.http.Fault;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.github.tomakehurst.wiremock.stubbing.Scenario;
import com.hazelcast.config.*;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import com.phonepe.drove.common.CommonTestUtils;
import com.phonepe.drove.models.api.ApiResponse;
import com.phonepe.drove.models.instance.InstanceInfo;
import com.phonepe.drove.models.instance.InstancePort;
import com.phonepe.drove.models.instance.LocalInstanceInfo;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.SneakyThrows;
import lombok.val;

import java.util.List;
import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.*;

/**
 *
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class DiscoveryTestUtils {

    public static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new ParameterNamesModule())
            .setSerializationInclusion(JsonInclude.Include.NON_EMPTY)
            .setSerializationInclusion(JsonInclude.Include.NON_NULL)
            .disable(SerializationFeature.FAIL_ON_EMPTY_BEANS)
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .enable(MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS);

    public static void createStubForNoPeer(final WireMockExtension controller) throws JsonProcessingException {
        controller.stubFor(get(urlEqualTo("/apis/v1/internal/instances"))
                        .withHeader("App-Instance-Authorization", equalTo("TestToken"))
                        .willReturn(badRequest()));
    }

    public static void createStubIOErrorr(final WireMockExtension controller) throws JsonProcessingException {
        controller.stubFor(get(urlEqualTo("/apis/v1/internal/instances"))
                        .withHeader("App-Instance-Authorization", equalTo("TestToken"))
                        .willReturn(aResponse().withFault(Fault.MALFORMED_RESPONSE_CHUNK)));
    }

    public static void createStubForFailed(final WireMockExtension controller) throws JsonProcessingException {
        val response = ApiResponse.failure("Forced fail");
        controller.stubFor(get(urlEqualTo("/apis/v1/internal/instances"))
                        .withHeader("App-Instance-Authorization", equalTo("TestToken"))
                        .willReturn(aResponse()
                                            .withStatus(200)
                                            .withHeader("Content-Type", "application/json")
                                            .withBody(MAPPER.writeValueAsBytes(response))));
    }

    public static void createStubForNoSuchPort(final WireMockExtension controller) throws JsonProcessingException {
        val instanceInfo = InstanceInfo.builder()
                .appId("1_0_0")
                .appName("test_app")
                .instanceId("instanceId")
                .executorId("ex1")
                .localInfo(LocalInstanceInfo.builder()
                                   .hostname("127.0.0.1")
                                   .ports(Map.of())
                                   .build())
                .build();
        val response = ApiResponse.success(List.of(instanceInfo));
        controller.stubFor(get(urlEqualTo("/apis/v1/internal/instances"))
                        .withHeader("App-Instance-Authorization", equalTo("TestToken"))
                        .willReturn(aResponse()
                                            .withStatus(200)
                                            .withHeader("Content-Type", "application/json")
                                            .withBody(MAPPER.writeValueAsBytes(response))));
    }

    public static void createStubForDNSFail(final WireMockExtension controller) throws JsonProcessingException {
        val instanceInfo = InstanceInfo.builder()
                .appId("1_0_0")
                .appName("test_app")
                .instanceId("instanceId")
                .executorId("ex1")
                .localInfo(LocalInstanceInfo.builder()
                                   .hostname("mars")
                                   .ports(Map.of("hazelcast", InstancePort.builder()
                                           .hostPort(5701)
                                           .build()))
                                   .build())
                .build();
        val response = ApiResponse.success(List.of(instanceInfo));
        controller.stubFor(get(urlEqualTo("/apis/v1/internal/instances"))
                        .withHeader("App-Instance-Authorization", equalTo("TestToken"))
                        .willReturn(aResponse()
                                            .withStatus(200)
                                            .withHeader("Content-Type", "application/json")
                                            .withBody(MAPPER.writeValueAsBytes(response))));
    }

    public static void createStubForSingleMemberDiscovery() throws JsonProcessingException {
        val instanceInfo = InstanceInfo.builder()
                .appId("1_0_0")
                .appName("test_app")
                .instanceId("instanceId")
                .executorId("ex1")
                .localInfo(LocalInstanceInfo.builder()
                                   .hostname("127.0.0.1")
                                   .ports(Map.of("hazelcast", InstancePort.builder()
                                           .hostPort(5701)
                                           .build()))
                                   .build())
                .build();
        val response = ApiResponse.success(List.of(instanceInfo));
        stubFor(get(urlEqualTo("/apis/v1/internal/instances"))
                        .withHeader("App-Instance-Authorization", equalTo("TestToken"))
                        .willReturn(aResponse()
                                            .withStatus(200)
                                            .withHeader("Content-Type", "application/json")
                                            .withBody(MAPPER.writeValueAsBytes(response))));
    }
    public static void createStubForSingleMemberDiscovery(final WireMockExtension controller) throws JsonProcessingException {
        createStubForSingleMemberDiscovery(controller, false);
    }

    public static void createStubForSingleMemberDiscovery(final WireMockExtension controller, boolean forApp) throws JsonProcessingException {
        val instanceInfo = InstanceInfo.builder()
                .appId("1_0_0")
                .appName("test_app")
                .instanceId("instanceId")
                .executorId("ex1")
                .localInfo(LocalInstanceInfo.builder()
                                   .hostname("127.0.0.1")
                                   .ports(Map.of("hazelcast", InstancePort.builder()
                                           .hostPort(5701)
                                           .build()))
                                   .build())
                .build();
        val response = ApiResponse.success(List.of(instanceInfo));
        controller.stubFor(get(urlEqualTo("/apis/v1/internal/instances" + (forApp ? "?forApp=true" : "")))
                        .withHeader("App-Instance-Authorization", equalTo("TestToken"))
                        .willReturn(aResponse()
                                            .withStatus(200)
                                            .withHeader("Content-Type", "application/json")
                                            .withBody(MAPPER.writeValueAsBytes(response))));
    }


    public static void createStubForSingleMemberDiscoveryWrong() throws JsonProcessingException {
        stubFor(get(urlEqualTo("/apis/v1/internal/instances"))
                        .withHeader("App-Instance-Authorization", equalTo("WrongToken"))
                        .willReturn(aResponse()
                                            .withStatus(401)
                                            .withHeader("Content-Type", "application/json")
                                            .withBody(MAPPER.writeValueAsBytes(ApiResponse.failure("Authorization failure")))));
    }

    public static void createStubForSingleMemberDiscoveryWithRetry() throws JsonProcessingException {
        stubFor(get(urlEqualTo("/apis/v1/internal/instances"))
                        .withHeader("App-Instance-Authorization", equalTo("TestToken"))
                        .inScenario("scenario")
                        .whenScenarioStateIs(Scenario.STARTED)
                        .willSetStateTo("passed")
                        .willReturn(aResponse()
                                            .withStatus(400)
                                            .withHeader("Content-Type", "application/json")
                                            .withBody(MAPPER.writeValueAsBytes("invalid state!!!"))));
        val instanceInfo = InstanceInfo.builder()
                .appId("1_0_0")
                .appName("test_app")
                .instanceId("instanceId")
                .executorId("ex1")
                .localInfo(LocalInstanceInfo.builder()
                                   .hostname("127.0.0.1")
                                   .ports(Map.of("hazelcast", InstancePort.builder()
                                           .hostPort(5701)
                                           .build()))
                                   .build())
                .build();
        val response = ApiResponse.success(List.of(instanceInfo));
        stubFor(get(urlEqualTo("/apis/v1/internal/instances"))
                        .withHeader("App-Instance-Authorization", equalTo("TestToken"))
                        .inScenario("scenario")
                        .whenScenarioStateIs("passed")
                        .willSetStateTo(Scenario.STARTED)
                        .willReturn(aResponse()
                                            .withStatus(200)
                                            .withHeader("Content-Type", "application/json")
                                            .withBody(MAPPER.writeValueAsBytes(response))));
    }

    public static void createStubForMultipleMembers() throws JsonProcessingException {
        val instanceInfo = InstanceInfo.builder()
                .appId("1_0_0")
                .appName("test_app")
                .instanceId("instanceId1")
                .executorId("ex1")
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
                .executorId("ex1")
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
                                            .withBody(MAPPER.writeValueAsBytes(response))));
    }

    public static HazelcastInstance getHazelcastInstance(int port) {
        return getHazelcastInstance(port, "TestToken");
    }

    @SneakyThrows
    public static HazelcastInstance getHazelcastInstance(int port, String token) {
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
