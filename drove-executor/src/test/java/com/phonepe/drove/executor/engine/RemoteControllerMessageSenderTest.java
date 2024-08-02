/*
 *  Copyright (c) 2022 Original Author(s), PhonePe India Pvt. Ltd.
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

package com.phonepe.drove.executor.engine;

import com.github.tomakehurst.wiremock.http.Fault;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import com.phonepe.drove.common.AbstractTestBase;
import com.phonepe.drove.auth.config.ClusterAuthenticationConfig;
import com.phonepe.drove.common.CommonUtils;
import com.phonepe.drove.common.model.MessageHeader;
import com.phonepe.drove.common.model.MessageResponse;
import com.phonepe.drove.common.model.controller.ExecutorSnapshotMessage;
import com.phonepe.drove.executor.discovery.ManagedLeadershipObserver;
import com.phonepe.drove.models.info.nodedata.ControllerNodeData;
import com.phonepe.drove.models.info.nodedata.NodeTransportType;
import lombok.SneakyThrows;
import lombok.val;
import org.junit.jupiter.api.Test;

import java.util.Date;
import java.util.Optional;

import static com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder.okForJson;
import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.phonepe.drove.common.model.MessageDeliveryStatus.ACCEPTED;
import static com.phonepe.drove.common.model.MessageDeliveryStatus.FAILED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 *
 */
@WireMockTest
class RemoteControllerMessageSenderTest extends AbstractTestBase {

    @Test
    @SneakyThrows
    void testBasicSend(final WireMockRuntimeInfo wm) {
        val leaderObserver = mock(ManagedLeadershipObserver.class);
        when(leaderObserver.leader())
                .thenReturn(Optional.of(new ControllerNodeData("localhost",
                                                               wm.getHttpPort(),
                                                               NodeTransportType.HTTP,
                                                               new Date(),
                                                               true)));

        val msgSender = new RemoteControllerMessageSender(leaderObserver,
                                                          AbstractTestBase.MAPPER,
                                                          ClusterAuthenticationConfig.DEFAULT,
                                                          CommonUtils.createHttpClient(false));

        val header = MessageHeader.executorRequest();
        stubFor(post("/apis/v1/messages")
                        .willReturn(okForJson(new MessageResponse(header, ACCEPTED))));
        assertEquals(ACCEPTED, msgSender.send(new ExecutorSnapshotMessage(header, null)).getStatus());
    }

    @Test
    @SneakyThrows
    void testSrvErr(final WireMockRuntimeInfo wm) {
        val leaderObserver = mock(ManagedLeadershipObserver.class);
        when(leaderObserver.leader())
                .thenReturn(Optional.of(new ControllerNodeData("localhost",
                                                               wm.getHttpPort(),
                                                               NodeTransportType.HTTP,
                                                               new Date(),
                                                               true)));

        val msgSender = new RemoteControllerMessageSender(leaderObserver,
                                                          AbstractTestBase.MAPPER,
                                                          ClusterAuthenticationConfig.DEFAULT,
                                                          CommonUtils.createHttpClient(false));

        val header = MessageHeader.executorRequest();
        stubFor(post("/apis/v1/messages")
                        .willReturn(serverError()));
        assertEquals(FAILED, msgSender.send(new ExecutorSnapshotMessage(header, null)).getStatus());
    }


    @Test
    @SneakyThrows
    void testIOException(final WireMockRuntimeInfo wm) {
        val leaderObserver = mock(ManagedLeadershipObserver.class);
        when(leaderObserver.leader())
                .thenReturn(Optional.of(new ControllerNodeData("localhost",
                                                               wm.getHttpPort(),
                                                               NodeTransportType.HTTP,
                                                               new Date(),
                                                               true)));

        val msgSender = new RemoteControllerMessageSender(leaderObserver,
                                                          AbstractTestBase.MAPPER,
                                                          ClusterAuthenticationConfig.DEFAULT,
                                                          CommonUtils.createHttpClient(false));

        val header = MessageHeader.executorRequest();
        stubFor(post("/apis/v1/messages")
                        .willReturn(aResponse().withFault(Fault.MALFORMED_RESPONSE_CHUNK)));
        assertEquals(FAILED, msgSender.send(new ExecutorSnapshotMessage(header, null)).getStatus());
    }

    @Test
    @SneakyThrows
    void testNoLeader() {
        val leaderObserver = mock(ManagedLeadershipObserver.class);
        when(leaderObserver.leader())
                .thenReturn(Optional.empty());

        val msgSender = new RemoteControllerMessageSender(leaderObserver,
                                                          AbstractTestBase.MAPPER,
                                                          ClusterAuthenticationConfig.DEFAULT,
                                                          CommonUtils.createHttpClient(false));

        val header = MessageHeader.executorRequest();
        assertEquals(FAILED, msgSender.send(new ExecutorSnapshotMessage(header, null)).getStatus());
    }

    @Test
    @SneakyThrows
    void testFailedStatus(final WireMockRuntimeInfo wm) {
        val leaderObserver = mock(ManagedLeadershipObserver.class);
        when(leaderObserver.leader())
                .thenReturn(Optional.of(new ControllerNodeData("localhost",
                                                               wm.getHttpPort(),
                                                               NodeTransportType.HTTP,
                                                               new Date(),
                                                               true)));

        val msgSender = new RemoteControllerMessageSender(leaderObserver,
                                                          AbstractTestBase.MAPPER,
                                                          ClusterAuthenticationConfig.DEFAULT,
                                                          CommonUtils.createHttpClient(false));

        val header = MessageHeader.executorRequest();
        stubFor(post("/apis/v1/messages")
                        .willReturn(okForJson(new MessageResponse(header, FAILED))));
        assertEquals(FAILED, msgSender.send(new ExecutorSnapshotMessage(header, null)).getStatus());
    }


}