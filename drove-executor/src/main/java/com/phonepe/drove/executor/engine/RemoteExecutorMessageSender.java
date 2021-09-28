package com.phonepe.drove.executor.engine;

import com.phonepe.drove.common.model.MessageDeliveryStatus;
import com.phonepe.drove.common.model.MessageResponse;
import com.phonepe.drove.common.model.controller.ControllerMessage;
import com.phonepe.drove.executor.discovery.LeadershipObserver;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.net.http.HttpClient;
import java.time.Duration;

/**
 *
 */
@Singleton
@Slf4j
public class RemoteExecutorMessageSender implements ExecutorMessageSender {
    private final LeadershipObserver observer;
    private final HttpClient httpClient;

    @Inject
    public RemoteExecutorMessageSender(LeadershipObserver observer) {
        this.observer = observer;
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
        log.info("Message sent to controller");
        return new MessageResponse(message.getHeader(), MessageDeliveryStatus.ACCEPTED); //TODO::SEND MESSAGE
    }
}
