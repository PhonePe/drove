package com.phonepe.drove.executor.engine;

import com.github.tomakehurst.wiremock.http.Fault;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import com.phonepe.drove.common.AbstractTestBase;
import com.phonepe.drove.common.auth.config.ClusterAuthenticationConfig;
import com.phonepe.drove.common.model.MessageHeader;
import com.phonepe.drove.common.model.MessageResponse;
import com.phonepe.drove.common.model.controller.ExecutorSnapshotMessage;
import com.phonepe.drove.executor.discovery.LeadershipObserver;
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
        val leaderObserver = mock(LeadershipObserver.class);
        when(leaderObserver.leader())
                .thenReturn(Optional.of(new ControllerNodeData("localhost",
                                                               wm.getHttpPort(),
                                                               NodeTransportType.HTTP,
                                                               new Date(),
                                                               true)));

        val msgSender = new RemoteControllerMessageSender(leaderObserver,
                                                          AbstractTestBase.MAPPER,
                                                          ClusterAuthenticationConfig.DEFAULT);

        val header = MessageHeader.executorRequest();
        stubFor(post("/apis/v1/messages")
                        .willReturn(okForJson(new MessageResponse(header, ACCEPTED))));
        assertEquals(ACCEPTED, msgSender.send(new ExecutorSnapshotMessage(header, null)).getStatus());
    }

    @Test
    @SneakyThrows
    void testSrvErr(final WireMockRuntimeInfo wm) {
        val leaderObserver = mock(LeadershipObserver.class);
        when(leaderObserver.leader())
                .thenReturn(Optional.of(new ControllerNodeData("localhost",
                                                               wm.getHttpPort(),
                                                               NodeTransportType.HTTP,
                                                               new Date(),
                                                               true)));

        val msgSender = new RemoteControllerMessageSender(leaderObserver,
                                                          AbstractTestBase.MAPPER,
                                                          ClusterAuthenticationConfig.DEFAULT);

        val header = MessageHeader.executorRequest();
        stubFor(post("/apis/v1/messages")
                        .willReturn(serverError()));
        assertEquals(FAILED, msgSender.send(new ExecutorSnapshotMessage(header, null)).getStatus());
    }


    @Test
    @SneakyThrows
    void testIOException(final WireMockRuntimeInfo wm) {
        val leaderObserver = mock(LeadershipObserver.class);
        when(leaderObserver.leader())
                .thenReturn(Optional.of(new ControllerNodeData("localhost",
                                                               wm.getHttpPort(),
                                                               NodeTransportType.HTTP,
                                                               new Date(),
                                                               true)));

        val msgSender = new RemoteControllerMessageSender(leaderObserver,
                                                          AbstractTestBase.MAPPER,
                                                          ClusterAuthenticationConfig.DEFAULT);

        val header = MessageHeader.executorRequest();
        stubFor(post("/apis/v1/messages")
                        .willReturn(aResponse().withFault(Fault.MALFORMED_RESPONSE_CHUNK)));
        assertEquals(FAILED, msgSender.send(new ExecutorSnapshotMessage(header, null)).getStatus());
    }
}