package com.phonepe.drove.common.net;

import com.codahale.metrics.SharedMetricRegistries;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import com.phonepe.drove.common.CommonUtils;
import com.phonepe.drove.auth.config.ClusterAuthenticationConfig;
import com.phonepe.drove.common.model.Message;
import com.phonepe.drove.common.model.MessageDeliveryStatus;
import com.phonepe.drove.common.model.MessageHeader;
import com.phonepe.drove.common.model.MessageResponse;
import com.phonepe.drove.common.retry.RetryOnAllExceptionsSpec;
import com.phonepe.drove.models.info.nodedata.NodeTransportType;
import com.phonepe.drove.models.info.nodedata.NodeType;
import io.appform.functionmetrics.FunctionMetricsManager;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.Value;
import lombok.val;
import net.jodah.failsafe.RetryPolicy;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder.okForJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.phonepe.drove.common.CommonUtils.configureMapper;
import static com.phonepe.drove.common.model.MessageDeliveryStatus.ACCEPTED;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 *
 */
@WireMockTest
class RemoteMessageSenderTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
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

    private static class TestMessageSender extends RemoteMessageSender<TestMessageType, TestMessage> {

        private final WireMockRuntimeInfo runtimeInfo;

        private TestMessageSender(WireMockRuntimeInfo runtimeInfo) {
            super(MAPPER, new ClusterAuthenticationConfig().setSecrets(List.of()), NodeType.CONTROLLER);
            this.runtimeInfo = runtimeInfo;
        }

        @Override
        protected RetryPolicy<MessageResponse> retryStrategy() {
            return CommonUtils.policy(new RetryOnAllExceptionsSpec(), null);
        }

        @Override
        protected Optional<RemoteHost> translateRemoteAddress(TestMessage message) {
            return Optional.of(new RemoteHost("localhost", runtimeInfo.getHttpPort(), NodeTransportType.HTTP));
        }
    }

    @BeforeAll
    static void setupClass() {
        configureMapper(MAPPER);
        FunctionMetricsManager.initialize("com.phonepe.drove", SharedMetricRegistries.getOrCreate("test"));
    }

    @Test
    void testMessageSend(final WireMockRuntimeInfo wireMockRuntimeInfo) {
        val header = MessageHeader.controllerRequest();
        stubFor(post("/apis/v1/messages")
                        .willReturn(okForJson(new MessageResponse(header, ACCEPTED))));
        val msgSender = new TestMessageSender(wireMockRuntimeInfo);
        val res = msgSender.send(new TestMessage(header, "Test"));
        assertEquals(MessageDeliveryStatus.ACCEPTED, res.getStatus());
    }
}