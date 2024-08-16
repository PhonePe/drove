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

package com.phonepe.drove.common.net;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.phonepe.drove.auth.config.ClusterAuthenticationConfig;
import com.phonepe.drove.auth.model.ClusterCommHeaders;
import com.phonepe.drove.common.CommonUtils;
import com.phonepe.drove.common.model.Message;
import com.phonepe.drove.common.model.MessageDeliveryStatus;
import com.phonepe.drove.common.model.MessageResponse;
import com.phonepe.drove.models.info.nodedata.NodeTransportType;
import com.phonepe.drove.models.info.nodedata.NodeType;
import io.appform.functionmetrics.MonitoredFunction;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import net.jodah.failsafe.Failsafe;
import net.jodah.failsafe.RetryPolicy;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.core5.http.ParseException;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.StringEntity;

import java.io.IOException;
import java.util.Optional;

import static com.phonepe.drove.auth.core.AuthConstants.NODE_ID_HEADER;
import static javax.ws.rs.core.HttpHeaders.CONTENT_TYPE;

/**
 *
 */
@Slf4j
@SuppressWarnings("java:S119")
public abstract class RemoteMessageSender<
        SendMessageType extends Enum<SendMessageType>,
        SendMessage extends Message<SendMessageType>>
        implements MessageSender<SendMessageType, SendMessage> {

    private final ObjectMapper mapper;
    private final CloseableHttpClient httpClient;
    private final ClusterAuthenticationConfig.SecretConfig secret;
    private final String nodeId;

    protected RemoteMessageSender(
            final ObjectMapper mapper,
            ClusterAuthenticationConfig clusterAuthenticationConfig,
            NodeType nodeType,
            CloseableHttpClient httpClient) {
        this.mapper = mapper;
        this.secret = clusterAuthenticationConfig.getSecrets()
                .stream()
                .filter(s -> s.getNodeType().equals(nodeType))
                .findAny()
                .orElse(null);
        this.httpClient = httpClient;
        nodeId = CommonUtils.hostname();
    }

    @Override
    @MonitoredFunction
    @SuppressWarnings("java:S1874")
    public MessageResponse send(SendMessage message) {
        val retryPolicy = retryStrategy();
        return Failsafe.with(retryPolicy).onFailure(result -> {
            val failure = result.getFailure();
            if (null != failure) {
                log.error("Message sending failed with error: {}", failure.getMessage());
            }
            else {
                log.error("Message sending failed with response: {}", result.getResult());
            }
        }).get(() -> sendRemoteMessage(message));
    }

    protected abstract RetryPolicy<MessageResponse> retryStrategy();

    @SuppressWarnings("deprecation")
    private MessageResponse sendRemoteMessage(SendMessage message) {
        val host = translateRemoteAddress(message).orElse(null);
        if (null == host) {
            log.error("No host found.");
            return new MessageResponse(message.getHeader(), MessageDeliveryStatus.FAILED);
        }
        val uri = String.format("%s://%s:%d/apis/v1/messages",
                                host.getTransportType() == NodeTransportType.HTTP
                                ? "http"
                                : "https",
                                host.getHostname(),
                                host.getPort());
        val request = new HttpPost(uri);
        try {
            request.setHeader(CONTENT_TYPE, "application/json");
            request.setHeader(NODE_ID_HEADER, nodeId);
            if(null != secret) {
                request.setHeader(ClusterCommHeaders.CLUSTER_AUTHORIZATION, secret.getSecret());
            }
            request.setEntity(new StringEntity(mapper.writeValueAsString(message)));
        }
        catch (JsonProcessingException e) {
            log.error("Error building message: ", e);
            return new MessageResponse(message.getHeader(), MessageDeliveryStatus.FAILED);
        }
        log.debug("Sending message to remote host: {}. Message: {}", uri, message);
        try(val response = httpClient.execute(request)) {
            val body = EntityUtils.toString(response.getEntity());
            if (response.getCode() == 200) {
                return mapper.readValue(body, MessageResponse.class);
            }
            else {
                log.info("Received non-200 response: {}", body);
            }
        }
        catch (IOException | ParseException e) {
            log.error("Error sending message: ", e);
            return new MessageResponse(message.getHeader(), MessageDeliveryStatus.FAILED);
        }
        log.info("Failed to send message to node: {}:{}", host.getHostname(), host.getPort());
        return new MessageResponse(message.getHeader(), MessageDeliveryStatus.FAILED);
    }

    protected abstract Optional<RemoteHost> translateRemoteAddress(final SendMessage message);

}
