package com.phonepe.drove.controller.engine;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.phonepe.drove.common.auth.ClusterAuthenticationConfig;
import com.phonepe.drove.common.model.ExecutorMessageType;
import com.phonepe.drove.common.model.MessageDeliveryStatus;
import com.phonepe.drove.common.model.MessageResponse;
import com.phonepe.drove.common.model.executor.ExecutorMessage;
import com.phonepe.drove.common.net.RemoteHost;
import com.phonepe.drove.common.net.RemoteMessageSender;
import com.phonepe.drove.models.info.nodedata.NodeType;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import net.jodah.failsafe.RetryPolicy;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.time.Duration;
import java.util.Optional;

/**
 *
 */
@Slf4j
@Singleton
public class RemoteExecutorMessageSender extends RemoteMessageSender<ExecutorMessageType, ExecutorMessage> {

    @Inject
    public RemoteExecutorMessageSender(
            ClusterAuthenticationConfig clusterAuthenticationConfig,
            ObjectMapper mapper) {
        super(mapper, clusterAuthenticationConfig, NodeType.CONTROLLER);
    }

    @Override
    protected RetryPolicy<MessageResponse> retryStrategy() {
        return new RetryPolicy<MessageResponse>()
                .withDelay(Duration.ofSeconds(1))
                .withMaxDuration(Duration.ofSeconds(30))
                .handle(Exception.class)
                .handleResultIf(response -> !MessageDeliveryStatus.ACCEPTED.equals(response.getStatus()));
    }

    @Override
    protected Optional<RemoteHost> translateRemoteAddress(ExecutorMessage message) {
        val host = message.getAddress();
        return Optional.of(new RemoteHost(host.getHostname(), host.getPort(), host.getTransportType()));
    }
}
