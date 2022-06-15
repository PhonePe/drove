package com.phonepe.drove.common.net;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.phonepe.drove.auth.config.ClusterAuthenticationConfig;
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

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;

import static com.phonepe.drove.auth.core.AuthConstansts.NODE_ID_HEADER;
import static javax.ws.rs.core.HttpHeaders.AUTHORIZATION;
import static javax.ws.rs.core.HttpHeaders.CONTENT_TYPE;

/**
 *
 */
@Slf4j
public abstract class RemoteMessageSender<
        SendMessageType extends Enum<SendMessageType>,
        SendMessage extends Message<SendMessageType>>
        implements MessageSender<SendMessageType, SendMessage> {

    private final ObjectMapper mapper;
    private final HttpClient httpClient;
    private final ClusterAuthenticationConfig.SecretConfig secret;
    private final String nodeId;

    protected RemoteMessageSender(
            final ObjectMapper mapper,
            ClusterAuthenticationConfig clusterAuthenticationConfig,
            NodeType nodeType) {
        this.mapper = mapper;
        this.secret = clusterAuthenticationConfig.getSecrets()
                .stream()
                .filter(s -> s.getNodeType().equals(nodeType))
                .findAny()
                .orElse(null);
        var connectionTimeout = Duration.ofSeconds(1);
        this.httpClient = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NEVER)
                .connectTimeout(connectionTimeout)
                .build();
        nodeId = CommonUtils.hostname();
    }

    @Override
    @MonitoredFunction
    public MessageResponse send(SendMessage message) {
        final RetryPolicy<MessageResponse> retryPolicy = retryStrategy();
        return Failsafe.with(retryPolicy).onFailure(result -> {
            val failure = result.getFailure();
            if (null != failure) {
                log.error("Message sending failed with error: {}", failure.getMessage());
            }
            else {
                val response = result.getResult();
                if (response.getStatus().equals(MessageDeliveryStatus.ACCEPTED)) {
                    log.error("Message sending failed with response: {}", response);
                }
            }
        }).get(() -> sendRemoteMessage(message));
    }

    protected abstract RetryPolicy<MessageResponse> retryStrategy();

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
        val requestBuilder = HttpRequest.newBuilder(URI.create(uri));
        try {

            requestBuilder.header(CONTENT_TYPE, "application/json");
            requestBuilder.header(NODE_ID_HEADER, nodeId);
            if(null != secret) {
                requestBuilder.header(AUTHORIZATION, "Bearer " + secret.getSecret());
            }
            requestBuilder.POST(HttpRequest.BodyPublishers.ofByteArray(mapper.writeValueAsBytes(message)));
        }
        catch (JsonProcessingException e) {
            log.error("Error building message: ", e);
            return new MessageResponse(message.getHeader(), MessageDeliveryStatus.FAILED);
        }
        log.debug("Sending message to remote host: {}. Message: {}", uri, message);
        val request = requestBuilder.timeout(Duration.ofSeconds(1)).build();
        try {
            val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            val body = response.body();
            if (response.statusCode() == 200) {
                return mapper.readValue(body, MessageResponse.class);
            }
            else {
                log.info("Received non-200 response: {}", body);
            }
        }
        catch (IOException e) {
            log.error("Error building message: ", e);
            return new MessageResponse(message.getHeader(), MessageDeliveryStatus.FAILED);
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        log.info("Failed to send message to node: {}:{}", host.getHostname(), host.getPort());
        return new MessageResponse(message.getHeader(), MessageDeliveryStatus.FAILED);
    }

    protected abstract Optional<RemoteHost> translateRemoteAddress(final SendMessage message);

}
