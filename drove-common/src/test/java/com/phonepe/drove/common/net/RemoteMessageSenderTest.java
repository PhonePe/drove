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

package com.phonepe.drove.common.net;

import com.github.tomakehurst.wiremock.http.Fault;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import com.phonepe.drove.auth.config.ClusterAuthenticationConfig;
import com.phonepe.drove.common.AbstractTestBase;
import com.phonepe.drove.common.CommonUtils;
import com.phonepe.drove.common.model.Message;
import com.phonepe.drove.common.model.MessageDeliveryStatus;
import com.phonepe.drove.common.model.MessageHeader;
import com.phonepe.drove.common.model.MessageResponse;
import com.phonepe.drove.models.info.nodedata.NodeTransportType;
import com.phonepe.drove.models.info.nodedata.NodeType;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.Value;
import lombok.val;
import net.jodah.failsafe.RetryPolicy;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Optional;

import static com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder.okForJson;
import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.phonepe.drove.common.model.MessageDeliveryStatus.ACCEPTED;
import static com.phonepe.drove.common.model.MessageDeliveryStatus.FAILED;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 *
 */
@WireMockTest
class RemoteMessageSenderTest extends AbstractTestBase {

    private enum TestMessageType {
        TEST_MESSAGE
    }

    @Value
    @EqualsAndHashCode(callSuper = true)
    @ToString(callSuper = true)
    private static class TestMessage extends Message<TestMessageType> {
        String data;

        public TestMessage(MessageHeader header, String data) {
            super(TestMessageType.TEST_MESSAGE, header);
            this.data = data;
        }
    }

    private static abstract class TestMessageSender extends RemoteMessageSender<TestMessageType, TestMessage> {

        private TestMessageSender() {
            super(MAPPER, ClusterAuthenticationConfig.DEFAULT, NodeType.CONTROLLER, CommonUtils.createHttpClient(false));
        }

        @Override
        protected RetryPolicy<MessageResponse> retryStrategy() {
            return new RetryPolicy<MessageResponse>()
                    .withDelay(Duration.ofSeconds(1))
                    .withMaxAttempts(1)
                    .handle(Exception.class)
                    .handleResultIf(response -> !MessageDeliveryStatus.ACCEPTED.equals(response.getStatus()));        }

    }

    @Test
    void testMessageSend(final WireMockRuntimeInfo wireMockRuntimeInfo) {
        val header = MessageHeader.controllerRequest();
        stubFor(post("/apis/v1/messages")
                        .willReturn(okForJson(new MessageResponse(header, ACCEPTED))));
        val msgSender = new TestMessageSender() {
            @Override
            protected Optional<RemoteHost> translateRemoteAddress(TestMessage message) {
                return Optional.of(new RemoteHost("localhost", wireMockRuntimeInfo.getHttpPort(), NodeTransportType.HTTP));
            }
        };
        val res = msgSender.send(new TestMessage(header, "Test"));
        assertEquals(MessageDeliveryStatus.ACCEPTED, res.getStatus());
    }
    @Test
    void testMessageSendNoSecret(final WireMockRuntimeInfo wireMockRuntimeInfo) {
        val header = MessageHeader.controllerRequest();
        stubFor(post("/apis/v1/messages")
                        .willReturn(okForJson(new MessageResponse(header, ACCEPTED))));
        val msgSender = new TestMessageSender() {
            @Override
            protected Optional<RemoteHost> translateRemoteAddress(TestMessage message) {
                return Optional.of(new RemoteHost("localhost", wireMockRuntimeInfo.getHttpPort(), NodeTransportType.HTTP));
            }
        };
        val res = msgSender.send(new TestMessage(header, "Test"));
        assertEquals(MessageDeliveryStatus.ACCEPTED, res.getStatus());
    }

    @Test
    void testServerError(final WireMockRuntimeInfo wireMockRuntimeInfo) {
        val header = MessageHeader.controllerRequest();
        stubFor(post("/apis/v1/messages")
                        .willReturn(serverError()));
        val msgSender = new TestMessageSender() {
            @Override
            protected Optional<RemoteHost> translateRemoteAddress(TestMessage message) {
                return Optional.of(new RemoteHost("localhost", wireMockRuntimeInfo.getHttpPort(), NodeTransportType.HTTP));
            }
        };
        val res = msgSender.send(new TestMessage(header, "Test"));
        assertEquals(MessageDeliveryStatus.FAILED, res.getStatus());
    }

    @Test
    void testIOError(final WireMockRuntimeInfo wireMockRuntimeInfo) {
        val header = MessageHeader.controllerRequest();
        stubFor(post("/apis/v1/messages")
                        .willReturn(aResponse().withFault(Fault.MALFORMED_RESPONSE_CHUNK)));
        val msgSender = new TestMessageSender() {
            @Override
            protected Optional<RemoteHost> translateRemoteAddress(TestMessage message) {
                return Optional.of(new RemoteHost("localhost", wireMockRuntimeInfo.getHttpPort(), NodeTransportType.HTTP));
            }
        };
        val res = msgSender.send(new TestMessage(header, "Test"));
        assertEquals(MessageDeliveryStatus.FAILED, res.getStatus());
    }

    @Test
    void testNoRemoteFound() {
        val header = MessageHeader.controllerRequest();
        val msgSender = new TestMessageSender() {

            @Override
            protected Optional<RemoteHost> translateRemoteAddress(TestMessage message) {
                return Optional.empty();
            }
        };
        val res = msgSender.send(new TestMessage(header, "Test"));
        assertEquals(MessageDeliveryStatus.FAILED, res.getStatus());
    }

    @Test
    void testFailedStatus(final WireMockRuntimeInfo wireMockRuntimeInfo) {
        val header = MessageHeader.controllerRequest();
        stubFor(post("/apis/v1/messages")
                        .willReturn(okForJson(new MessageResponse(header, FAILED))));
        val msgSender = new TestMessageSender() {
            @Override
            protected Optional<RemoteHost> translateRemoteAddress(TestMessage message) {
                return Optional.of(new RemoteHost("localhost", wireMockRuntimeInfo.getHttpPort(), NodeTransportType.HTTP));
            }
        };
        val res = msgSender.send(new TestMessage(header, "Test"));
        assertEquals(FAILED, res.getStatus());
    }
}