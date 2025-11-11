/*
 *  Copyright (c) 2025 Original Author(s), PhonePe India Pvt. Ltd.
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

package com.phonepe.drove.executor.discovery;

import com.google.common.annotations.VisibleForTesting;
import com.phonepe.drove.common.discovery.leadership.LeadershipObserver;
import com.phonepe.drove.models.info.nodedata.ControllerNodeData;
import com.phonepe.drove.models.info.nodedata.NodeTransportType;
import io.appform.signals.signals.ScheduledSignal;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;

import javax.inject.Singleton;
import javax.ws.rs.core.UriBuilder;
import java.net.URL;
import java.time.Duration;
import java.util.Date;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.StampedLock;

/**
 * Checks for cluster leader by pinging the specified controllers in the cluster.
 */
@Singleton
@Slf4j
public class RemoteLeadershipObserver implements LeadershipObserver {
    private static final String HANDLER = "__LEADER_FINDER__";
    @VisibleForTesting
    static final String PING_API = "/apis/v1/ping";

    private final ControllerConfig controllerConfig;
    private final CloseableHttpClient httpClient;

    private final AtomicReference<ControllerNodeData> leader = new AtomicReference<>(null);
    private final StampedLock leaderLock = new StampedLock();
    private final ScheduledSignal scheduledSignal;

    public RemoteLeadershipObserver(
            ControllerConfig controllerConfig,
            CloseableHttpClient httpClient,
            Duration checkInterval) {
        this.controllerConfig = controllerConfig;
        this.httpClient = httpClient;
        this.scheduledSignal = new ScheduledSignal(checkInterval);
    }

    @Override
    public Optional<ControllerNodeData> leader() {
        return Optional.ofNullable(leader.get());
    }

    @Override
    public void start() {
        scheduledSignal.connect(HANDLER,
                                date -> {
                                    findLeader();
                                    log.debug("Leader finder task for {} completed.", date);
                                });
    }

    @Override
    public void stop() {
        scheduledSignal.disconnect(HANDLER);
        scheduledSignal.close();
    }

    private void findLeader() {
        var stamp = leaderLock.tryReadLock();
        if (stamp == 0) {
            log.info("Looks like another attempt is already underway. Skipping this leader check");
            return;
        }
        try {
            final var controllers = controllerConfig.getEndpoints();
            final var leaderController = controllers.stream()
                    .filter(this::pingEndpoint)
                    .findAny()
                    .map(leaderEndpoint -> {
                        val protocol = leaderEndpoint.getProtocol();
                        return new ControllerNodeData(leaderEndpoint.getHost(),
                                                      leaderEndpoint.getPort(),
                                                      protocol.equals("https") ? NodeTransportType.HTTPS
                                                                               : NodeTransportType.HTTP,
                                                      new Date(),
                                                      true);
                    })
                    .orElse(null);

            val oldLeader = leader.get();
            if (null == leaderController) {
                log.warn("No leader in the cluster");
            }
            val leaderChanged = (null == oldLeader && null != leaderController)
                    || (null != oldLeader && null == leaderController)
                    || (null != oldLeader && (!oldLeader.getHostname()
                    .equals(leaderController.getHostname()) || oldLeader.getPort() != leaderController.getPort()));
            if (leaderChanged) {
                val status = leaderLock.tryConvertToWriteLock(stamp);
                if (0 == status) {
                    leaderLock.unlockRead(stamp);
                    stamp = leaderLock.readLock();
                }
                else {
                    stamp = status;
                }
                leader.set(leaderController);
                log.info("Leader changed from {} to {}", oldLeader, leaderController);
            }
        }
        finally {
            leaderLock.unlock(stamp);
        }
    }

    private boolean pingEndpoint(URL endpoint) {
        try {
            return httpClient.execute(new HttpGet(UriBuilder.fromUri(endpoint.toURI())
                                                          .path(PING_API)
                                                          .build()),
                                      response -> {
                                          final var responseCode = response.getCode();
                                          if (responseCode == 200) {
                                              return true;
                                          }
                                          else {
                                              log.debug("Response for URI: {} is {}",
                                                        endpoint,
                                                        responseCode);
                                          }
                                          return false;
                                      });
        }
        catch (Throwable t) {
            log.error("Error calling endpoint %s".formatted(endpoint), t);
        }
        return false;
    }
}
