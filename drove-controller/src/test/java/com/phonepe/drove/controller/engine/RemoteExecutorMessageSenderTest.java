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

package com.phonepe.drove.controller.engine;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import com.phonepe.drove.auth.config.ClusterAuthenticationConfig;
import com.phonepe.drove.common.CommonUtils;
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
        val ms = new RemoteExecutorMessageSender(ClusterAuthenticationConfig.DEFAULT, MAPPER, CommonUtils.createHttpClient(false));

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