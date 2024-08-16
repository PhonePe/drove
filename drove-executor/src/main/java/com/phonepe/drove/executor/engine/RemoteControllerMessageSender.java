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

package com.phonepe.drove.executor.engine;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.phonepe.drove.auth.config.ClusterAuthenticationConfig;
import com.phonepe.drove.common.model.ControllerMessageType;
import com.phonepe.drove.common.model.MessageDeliveryStatus;
import com.phonepe.drove.common.model.MessageResponse;
import com.phonepe.drove.common.model.controller.ControllerMessage;
import com.phonepe.drove.common.net.RemoteHost;
import com.phonepe.drove.common.net.RemoteMessageSender;
import com.phonepe.drove.executor.discovery.ManagedLeadershipObserver;
import com.phonepe.drove.models.info.nodedata.NodeType;
import lombok.extern.slf4j.Slf4j;
import net.jodah.failsafe.RetryPolicy;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;

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
    private final ManagedLeadershipObserver observer;

    @Inject
    public RemoteControllerMessageSender(
            ManagedLeadershipObserver observer,
            ObjectMapper mapper,
            ClusterAuthenticationConfig clusterAuthenticationConfig,
            CloseableHttpClient httpClient) {
        super(mapper, clusterAuthenticationConfig, NodeType.EXECUTOR, httpClient);
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
