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

package com.phonepe.drove.executor.discovery;

import com.github.tomakehurst.wiremock.http.Fault;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import com.phonepe.drove.auth.config.ClusterAuthenticationConfig;
import com.phonepe.drove.common.AbstractTestBase;
import com.phonepe.drove.executor.managed.ExecutorIdManager;
import com.phonepe.drove.models.api.ApiResponse;
import com.phonepe.drove.models.info.nodedata.ControllerNodeData;
import com.phonepe.drove.models.info.nodedata.NodeTransportType;
import com.phonepe.drove.models.internal.KnownInstancesData;
import com.phonepe.drove.models.internal.LocalServiceInstanceResources;
import lombok.SneakyThrows;
import lombok.val;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Date;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests {@link ClusterClient}
 */
@WireMockTest
class ClusterClientTest extends AbstractTestBase {
    private final ExecutorIdManager eIdMan = mock(ExecutorIdManager.class);
    private final ManagedLeadershipObserver leObs = mock(ManagedLeadershipObserver.class);

    @BeforeEach
    void setupMocks() {
        when(eIdMan.executorId()).thenReturn(Optional.of("Ex1"));
    }

    @AfterEach
    void resetMocks() {
        Mockito.reset(eIdMan, leObs);
    }

    @Test
    @SneakyThrows
    void testLastKnownInstances(WireMockRuntimeInfo wm) {
        stubFor(get(urlEqualTo("/apis/v1/internal/cluster/executors/Ex1/instances/last"))
                        .willReturn(okJson(MAPPER.writeValueAsString(
                                ApiResponse.success(new KnownInstancesData(Set.of("AI1"), Set.of(), Set.of(), Set.of(), Set.of(), Set.of()))))));
        when(leObs.leader()).thenReturn(Optional.of(new ControllerNodeData("localhost",
                                                                           wm.getHttpPort(),
                                                                           NodeTransportType.HTTP,
                                                                           new Date(),
                                                                           true)));

        val clusterClient = new ClusterClient(eIdMan, leObs, MAPPER, ClusterAuthenticationConfig.DEFAULT);
        val res = clusterClient.lastKnownInstances();
        assertEquals(1, res.getAppInstanceIds().size());
    }

    @Test
    @SneakyThrows
    void testLastKnownInstanceNoData(WireMockRuntimeInfo wm) {
        stubFor(get(urlEqualTo("/apis/v1/internal/cluster/executors/Ex1/instances/last"))
                        .willReturn(okJson(MAPPER.writeValueAsString(ApiResponse.success(null)))));
        when(leObs.leader()).thenReturn(Optional.of(new ControllerNodeData("localhost",
                                                                           wm.getHttpPort(),
                                                                           NodeTransportType.HTTP,
                                                                           new Date(),
                                                                           true)));

        val clusterClient = new ClusterClient(eIdMan, leObs, MAPPER, ClusterAuthenticationConfig.DEFAULT);
        val res = clusterClient.lastKnownInstances();
        assertEquals(KnownInstancesData.EMPTY, res);
    }

    @Test
    void testLastKnownInstanceCallFailure(WireMockRuntimeInfo wm) {
        stubFor(get(urlEqualTo("/apis/v1/internal/cluster/executors/Ex1/instances/last"))
                        .willReturn(serverError()));
        when(leObs.leader()).thenReturn(Optional.of(new ControllerNodeData("localhost",
                                                                           wm.getHttpPort(),
                                                                           NodeTransportType.HTTP,
                                                                           new Date(),
                                                                           true)));

        val clusterClient = new ClusterClient(eIdMan, leObs, MAPPER, ClusterAuthenticationConfig.DEFAULT);
        assertEquals(KnownInstancesData.EMPTY, clusterClient.lastKnownInstances());
    }

    @Test
    void testLastKnownInstanceNetworkError(WireMockRuntimeInfo wm) {
        stubFor(get(urlEqualTo("/apis/v1/internal/cluster/executors/Ex1/instances/last"))
                        .willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)));
        when(leObs.leader()).thenReturn(Optional.of(new ControllerNodeData("localhost",
                                                                           wm.getHttpPort(),
                                                                           NodeTransportType.HTTP,
                                                                           new Date(),
                                                                           true)));

        val clusterClient = new ClusterClient(eIdMan, leObs, MAPPER, ClusterAuthenticationConfig.DEFAULT);
        assertEquals(KnownInstancesData.EMPTY, clusterClient.lastKnownInstances());
    }

    @Test
    void testLastKnownInstanceNoLeader() {
        when(leObs.leader()).thenReturn(Optional.empty());
        val clusterClient = new ClusterClient(eIdMan, leObs, MAPPER, ClusterAuthenticationConfig.DEFAULT);
        assertEquals(KnownInstancesData.EMPTY, clusterClient.lastKnownInstances());
    }

    @Test
    void testLastKnownInstanceNoExecutorId(WireMockRuntimeInfo wm) {
        when(eIdMan.executorId()).thenReturn(Optional.of("Ex1"));
        when(leObs.leader()).thenReturn(Optional.of(new ControllerNodeData("localhost",
                                                                           wm.getHttpPort(),
                                                                           NodeTransportType.HTTP,
                                                                           new Date(),
                                                                           true)));        val clusterClient = new ClusterClient(eIdMan, leObs, MAPPER, ClusterAuthenticationConfig.DEFAULT);
        assertEquals(KnownInstancesData.EMPTY, clusterClient.lastKnownInstances());
    }

    @Test
    @SneakyThrows
    void testCurrentKnownInstances(WireMockRuntimeInfo wm) {
        stubFor(get(urlEqualTo("/apis/v1/internal/cluster/executors/Ex1/instances/current"))
                        .willReturn(okJson(MAPPER.writeValueAsString(
                                ApiResponse.success(new KnownInstancesData(Set.of("AI1"), Set.of(), Set.of(), Set.of(), Set.of(), Set.of()))))));
        when(leObs.leader()).thenReturn(Optional.of(new ControllerNodeData("localhost",
                                                                           wm.getHttpPort(),
                                                                           NodeTransportType.HTTP,
                                                                           new Date(),
                                                                           true)));

        val clusterClient = new ClusterClient(eIdMan, leObs, MAPPER, ClusterAuthenticationConfig.DEFAULT);
        val res = clusterClient.currentKnownInstances();
        assertEquals(1, res.getAppInstanceIds().size());
    }

    @Test
    @SneakyThrows
    void testReservedResources(WireMockRuntimeInfo wm) {
        stubFor(get(urlEqualTo("/apis/v1/internal/cluster/resources/reserved"))
                        .willReturn(okJson(MAPPER.writeValueAsString(
                                ApiResponse.success(new LocalServiceInstanceResources(1, 128, Map.of("ls1", 1)))))));
        when(leObs.leader()).thenReturn(Optional.of(new ControllerNodeData("localhost",
                                                                           wm.getHttpPort(),
                                                                           NodeTransportType.HTTP,
                                                                           new Date(),
                                                                           true)));

        val clusterClient = new ClusterClient(eIdMan, leObs, MAPPER, ClusterAuthenticationConfig.DEFAULT);
        val res = clusterClient.reservedResources();
        assertEquals(1, res.getCpus());
        assertEquals(128, res.getMemory());
        assertEquals(Map.of("ls1", 1), res.getRequiredInstances());
    }

    @Test
    @SneakyThrows
    void testReservedResourcesNoData(WireMockRuntimeInfo wm) {
        stubFor(get(urlEqualTo("/apis/v1/internal/cluster/resources/reserved"))
                        .willReturn(okJson(MAPPER.writeValueAsString(ApiResponse.success(null)))));
        when(leObs.leader()).thenReturn(Optional.of(new ControllerNodeData("localhost",
                                                                           wm.getHttpPort(),
                                                                           NodeTransportType.HTTP,
                                                                           new Date(),
                                                                           true)));

        val clusterClient = new ClusterClient(eIdMan, leObs, MAPPER, ClusterAuthenticationConfig.DEFAULT);
        val res = clusterClient.reservedResources();
        assertEquals(0, res.getCpus());
        assertEquals(0, res.getMemory());
        assertEquals(Map.of(), res.getRequiredInstances());
    }

    @Test
    void testReservedResourcesCallFailure(WireMockRuntimeInfo wm) {
        stubFor(get(urlEqualTo("/apis/v1/internal/cluster/resources/reserved"))
                        .willReturn(serverError()));
        when(leObs.leader()).thenReturn(Optional.of(new ControllerNodeData("localhost",
                                                                           wm.getHttpPort(),
                                                                           NodeTransportType.HTTP,
                                                                           new Date(),
                                                                           true)));

        val clusterClient = new ClusterClient(eIdMan, leObs, MAPPER, ClusterAuthenticationConfig.DEFAULT);
        assertThrows(ControllerCommunicationError.class, clusterClient::reservedResources);
    }

    @Test
    void testReservedResourcesNetworkError(WireMockRuntimeInfo wm) {
        stubFor(get(urlEqualTo("/apis/v1/internal/cluster/resources/reserved"))
                        .willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)));
        when(leObs.leader()).thenReturn(Optional.of(new ControllerNodeData("localhost",
                                                                           wm.getHttpPort(),
                                                                           NodeTransportType.HTTP,
                                                                           new Date(),
                                                                           true)));

        val clusterClient = new ClusterClient(eIdMan, leObs, MAPPER, ClusterAuthenticationConfig.DEFAULT);
        assertThrows(ControllerCommunicationError.class, clusterClient::reservedResources);
    }

    @Test
    void testReservedResourcesNoLeader() {
        when(leObs.leader()).thenReturn(Optional.empty());
        val clusterClient = new ClusterClient(eIdMan, leObs, MAPPER, ClusterAuthenticationConfig.DEFAULT);
        assertThrows(ControllerCommunicationError.class, clusterClient::reservedResources);
    }


}