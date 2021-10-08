package com.phonepe.drove.executor.engine;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.phonepe.drove.common.model.MessageDeliveryStatus;
import com.phonepe.drove.common.model.MessageResponse;
import com.phonepe.drove.common.model.controller.ControllerMessage;
import com.phonepe.drove.executor.discovery.LeadershipObserver;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 *
 */
@Singleton
@Slf4j
public class RemoteExecutorMessageSender implements ExecutorMessageSender {
    private final LeadershipObserver observer;
    private final HttpClient httpClient;
    private final ObjectMapper mapper;

    @Inject
    public RemoteExecutorMessageSender(LeadershipObserver observer, ObjectMapper mapper) {
        this.observer = observer;
        this.mapper = mapper;
        var connectionTimeout = Duration.ofSeconds(1);
        httpClient = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NEVER)
                .connectTimeout(connectionTimeout)
                .build();
    }

    public MessageResponse sendRemoteMessage(final ControllerMessage message) {
        val leader = observer.leader().orElse(null);
        if(null == leader) {
            log.error("No leader controller found");
            return new MessageResponse(message.getHeader(), MessageDeliveryStatus.FAILED);
        }
        val uri = String.format("http://%s:%d/messages/v1", leader.getHostname(), leader.getPort());
        val requestBuilder = HttpRequest.newBuilder(URI.create(uri));
        try {
            requestBuilder.header("Content-type", "application/json");
            requestBuilder.POST(HttpRequest.BodyPublishers.ofByteArray(mapper.writeValueAsBytes(message)));
        }
        catch (JsonProcessingException e) {
            log.error("Error building message: ", e);
            return new MessageResponse(message.getHeader(), MessageDeliveryStatus.FAILED);
        }
        log.debug("Sending message to leader: {}", uri);
        val request = requestBuilder.timeout(Duration.ofSeconds(1)).build();
        try {
            val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            val body = response.body();
            if(response.statusCode() == 200) {
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
        log.info("Message sent to controller");
        return new MessageResponse(message.getHeader(), MessageDeliveryStatus.FAILED);
    }
}
