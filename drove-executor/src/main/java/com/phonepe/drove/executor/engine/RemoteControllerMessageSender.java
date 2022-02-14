package com.phonepe.drove.executor.engine;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.phonepe.drove.common.auth.config.ClusterAuthenticationConfig;
import com.phonepe.drove.common.model.ControllerMessageType;
import com.phonepe.drove.common.model.MessageDeliveryStatus;
import com.phonepe.drove.common.model.MessageResponse;
import com.phonepe.drove.common.model.controller.ControllerMessage;
import com.phonepe.drove.common.net.RemoteHost;
import com.phonepe.drove.common.net.RemoteMessageSender;
import com.phonepe.drove.executor.discovery.LeadershipObserver;
import com.phonepe.drove.models.info.nodedata.NodeType;
import lombok.extern.slf4j.Slf4j;
import net.jodah.failsafe.RetryPolicy;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.time.Duration;
import java.util.Optional;

/**
 * Sends messages to the current leader
 */
@Singleton
@Slf4j
public class RemoteControllerMessageSender extends RemoteMessageSender<ControllerMessageType, ControllerMessage> {
    private final LeadershipObserver observer;

    @Inject
    public RemoteControllerMessageSender(
            LeadershipObserver observer, ObjectMapper mapper, ClusterAuthenticationConfig clusterAuthenticationConfig) {
        super(mapper, clusterAuthenticationConfig, NodeType.EXECUTOR);
        this.observer = observer;
    }

    @Override
    protected RetryPolicy<MessageResponse> retryStrategy() {
        return new RetryPolicy<MessageResponse>()
                .withDelay(Duration.ofSeconds(1))
                .withMaxAttempts(3)
                .handle(Exception.class)
                .handleResultIf(response -> !MessageDeliveryStatus.ACCEPTED.equals(response.getStatus()));
    }

    @Override
    protected Optional<RemoteHost> translateRemoteAddress(ControllerMessage message) {
        return observer.leader().map(leader -> new RemoteHost(leader.getHostname(),
                                                              leader.getPort(),
                                                              leader.getTransportType()));
    }
}
