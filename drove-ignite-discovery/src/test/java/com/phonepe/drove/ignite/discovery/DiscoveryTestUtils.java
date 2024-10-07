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

package com.phonepe.drove.ignite.discovery;


import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.module.paramnames.ParameterNamesModule;
import com.github.tomakehurst.wiremock.http.Fault;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.phonepe.drove.ignite.discovery.config.DroveIgniteConfig;
import com.phonepe.drove.models.api.ApiResponse;
import com.phonepe.drove.models.instance.InstanceInfo;
import com.phonepe.drove.models.instance.InstancePort;
import com.phonepe.drove.models.instance.LocalInstanceInfo;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.val;
import org.apache.ignite.Ignite;
import org.apache.ignite.Ignition;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.badRequest;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;

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

    public static void createStubForSingleMemberDiscovery(final String discoveryPortName,
                                                          final String commPortName,
                                                          final int discoveryHostPort,
                                                          final int discoveryContainerPort,
                                                          final int commHostPort,
                                                          final int commContainerPort,
                                                          final String instanceId) throws JsonProcessingException, UnknownHostException {
        val instanceInfo = InstanceInfo.builder()
                .appId("1_0_0")
                .appName("test_app")
                .instanceId(instanceId)
                .executorId("ex1")
                .localInfo(LocalInstanceInfo.builder()
                        .hostname(InetAddress.getLocalHost().getHostAddress())
                        .ports(Map.of(discoveryPortName, InstancePort.builder()
                                        .hostPort(discoveryHostPort)
                                        .containerPort(discoveryContainerPort)
                                        .build(), commPortName,
                                InstancePort.builder()
                                        .containerPort(commContainerPort)
                                        .hostPort(commHostPort)
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

    public static void createStubForMultipleMembers(final InstanceInfo instanceInfo1,
                                                    final InstanceInfo instanceInfo2) throws JsonProcessingException {
        val response = ApiResponse.success(List.of(instanceInfo1, instanceInfo2));
        stubFor(get(urlEqualTo("/apis/v1/internal/instances"))
                .withHeader("App-Instance-Authorization", equalTo("TestToken"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(MAPPER.writeValueAsBytes(response))));
    }

    public static void createStubForSingleMemberDiscovery(final WireMockExtension controller,
                                                          final String discoveryPortName,
                                                          final String containerPortName,
                                                          final String instanceId) throws JsonProcessingException {
        createStubForSingleMemberDiscovery(controller, false, discoveryPortName, containerPortName, instanceId);
    }

    public static void createStubForSingleMemberDiscovery(final WireMockExtension controller,
                                                          final String discoveryPortName,
                                                          final String containerPortName) throws JsonProcessingException {
        createStubForSingleMemberDiscovery(controller, false, discoveryPortName, containerPortName, UUID.randomUUID().toString());
    }

    public static void createStubForSingleMemberDiscovery(final WireMockExtension controller,
                                                          final boolean forApp) throws JsonProcessingException {
        createStubForSingleMemberDiscovery(controller, forApp, UUID.randomUUID().toString(), UUID.randomUUID().toString(),
                UUID.randomUUID().toString());
    }

    public static void createStubForSingleMemberDiscoveryWithInstanceId(final WireMockExtension controller,
                                                                        final boolean forApp,
                                                                        final String instanceId) throws JsonProcessingException {
        createStubForSingleMemberDiscovery(controller, forApp, UUID.randomUUID().toString(), UUID.randomUUID().toString(), instanceId);
    }

    public static void createStubForSingleMemberDiscovery(final WireMockExtension controller,
                                                          boolean forApp,
                                                          final String discoveryPortName,
                                                          final String containerPortName,
                                                          final String instanceId) throws JsonProcessingException {
        val instanceInfo = InstanceInfo.builder()
                .appId("1_0_0")
                .appName("test_app")
                .instanceId(instanceId)
                .executorId("ex1")
                .localInfo(LocalInstanceInfo.builder()
                        .hostname("127.0.0.1")
                        .ports(Map.of(discoveryPortName, InstancePort.builder()
                                .hostPort(47101)
                                .containerPort(47100)
                                .build(), containerPortName, InstancePort.builder()
                                .hostPort(47103)
                                .containerPort(47102)
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

    public static void createStubForSingleMemberDiscovery(final WireMockExtension controller,
                                                          final int containerPort) throws JsonProcessingException {
        createStubForSingleMemberDiscovery(controller, false, containerPort);
    }

    public static void createStubForSingleMemberDiscovery(final WireMockExtension controller,
                                                          boolean forApp,
                                                          final int containerPort) throws JsonProcessingException {
        val instanceInfo = InstanceInfo.builder()
                .appId("1_0_0")
                .appName("test_app")
                .instanceId("instanceId")
                .executorId("ex1")
                .localInfo(LocalInstanceInfo.builder()
                        .hostname("127.0.0.1")
                        .ports(Map.of("ignite", InstancePort.builder()
                                .hostPort(47100)
                                .containerPort(containerPort)
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


    public static Ignite getIgniteInstance(final String discoveryPortName,
                                           final String commPortName) {
        val igniteConfigProvider = new IgniteConfigProvider();
        val igniteConfiguration = igniteConfigProvider.provideIgniteConfiguration(DroveIgniteConfig.builder()
                .useAppNameForDiscovery(false)
                .droveEndpoint("http://127.0.0.1:8878,http://127.0.0.1:8878")
                .discoveryPortName(discoveryPortName)
                .communicationPortName(commPortName)
                .build());
        igniteConfiguration.setIgniteInstanceName("instance" + discoveryPortName + UUID.randomUUID());
        return Ignition.start(igniteConfiguration);
    }
}

