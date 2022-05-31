package com.phonepe.drove.controller.engine;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import com.phonepe.drove.common.auth.config.ClusterAuthenticationConfig;
import com.phonepe.drove.common.model.MessageHeader;
import com.phonepe.drove.common.model.MessageResponse;
import com.phonepe.drove.common.model.executor.BlacklistExecutorMessage;
import com.phonepe.drove.common.model.executor.ExecutorAddress;
import com.phonepe.drove.controller.ControllerTestBase;
import com.phonepe.drove.controller.ControllerTestUtils;
import com.phonepe.drove.models.info.nodedata.NodeTransportType;
import lombok.val;
import org.junit.jupiter.api.Test;

import static com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder.okForJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.phonepe.drove.common.model.MessageDeliveryStatus.ACCEPTED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 *
 */
@WireMockTest
class RemoteExecutorMessageSenderTest extends ControllerTestBase {

    @Test
    void testSend(final WireMockRuntimeInfo wm) {
        val ms = new RemoteExecutorMessageSender(ClusterAuthenticationConfig.DEFAULT, MAPPER);

        assertTrue(true);

        val header = MessageHeader.controllerRequest();
        stubFor(post("/apis/v1/messages")
                        .willReturn(okForJson(new MessageResponse(header, ACCEPTED))));
        assertEquals(ACCEPTED,
                     ms.send(new BlacklistExecutorMessage(header,
                                                          new ExecutorAddress(ControllerTestUtils.EXECUTOR_ID,
                                                                              "localhost",
                                                                              wm.getHttpPort(),
                                                                              NodeTransportType.HTTP))).getStatus());
    }
}