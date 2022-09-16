package com.phonepe.drove.hazelcast.discovery;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.module.paramnames.ParameterNamesModule;
import com.github.tomakehurst.wiremock.http.Fault;
import com.github.tomakehurst.wiremock.stubbing.Scenario;
import com.phonepe.drove.models.api.ApiResponse;
import com.phonepe.drove.models.instance.InstanceInfo;
import com.phonepe.drove.models.instance.InstancePort;
import com.phonepe.drove.models.instance.LocalInstanceInfo;
import lombok.experimental.UtilityClass;
import lombok.val;

import java.util.List;
import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.*;

/**
 *
 */
@UtilityClass
public class DiscoveryTestUtils {

    public static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new ParameterNamesModule())
            .setSerializationInclusion(JsonInclude.Include.NON_EMPTY)
            .setSerializationInclusion(JsonInclude.Include.NON_NULL)
            .disable(SerializationFeature.FAIL_ON_EMPTY_BEANS)
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .enable(MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS);

    public static void createStubForNoPeer() throws JsonProcessingException {
        stubFor(get(urlEqualTo("/apis/v1/internal/instances"))
                        .withHeader("App-Instance-Authorization", equalTo("TestToken"))
                        .willReturn(badRequest()));
    }

    public static void createStubIOErrorr() throws JsonProcessingException {
        stubFor(get(urlEqualTo("/apis/v1/internal/instances"))
                        .withHeader("App-Instance-Authorization", equalTo("TestToken"))
                        .willReturn(aResponse().withFault(Fault.MALFORMED_RESPONSE_CHUNK)));
    }

    public static void createStubForFailed() throws JsonProcessingException {
        val response = ApiResponse.failure("Forced fail");
        stubFor(get(urlEqualTo("/apis/v1/internal/instances"))
                        .withHeader("App-Instance-Authorization", equalTo("TestToken"))
                        .willReturn(aResponse()
                                            .withStatus(200)
                                            .withHeader("Content-Type", "application/json")
                                            .withBody(MAPPER.writeValueAsBytes(response))));
    }

    public static void createStubForNoSuchPort() throws JsonProcessingException {
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
        stubFor(get(urlEqualTo("/apis/v1/internal/instances"))
                        .withHeader("App-Instance-Authorization", equalTo("TestToken"))
                        .willReturn(aResponse()
                                            .withStatus(200)
                                            .withHeader("Content-Type", "application/json")
                                            .withBody(MAPPER.writeValueAsBytes(response))));
    }

    public static void createStubForDNSFail() throws JsonProcessingException {
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
        stubFor(get(urlEqualTo("/apis/v1/internal/instances"))
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

}
