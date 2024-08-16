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

package com.phonepe.drove.common.discovery.leadership;

import com.phonepe.drove.common.discovery.NodeDataStore;
import com.phonepe.drove.models.info.nodedata.ControllerNodeData;
import com.phonepe.drove.models.info.nodedata.ExecutorNodeData;
import com.phonepe.drove.models.info.nodedata.NodeDataVisitor;
import com.phonepe.drove.models.info.nodedata.NodeType;
import io.appform.signals.signals.ScheduledSignal;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.time.Duration;
import java.util.Date;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

/**
 *
 */
@Slf4j
@Singleton
public class LeadershipObserver {

    private static final String LEADERSHIP_OBSERVER_HANDLER = "LEADERSHIP_OBSERVER_HANDLER";

    private final ScheduledSignal leaderRefresher = new ScheduledSignal(Duration.ofSeconds(5));
    private final NodeDataStore nodeDataStore;
    private final AtomicReference<ControllerNodeData> leader = new AtomicReference<>();

    @Inject
    public LeadershipObserver(NodeDataStore nodeDataStore) {
        this.nodeDataStore = nodeDataStore;
    }

    public Optional<ControllerNodeData> leader() {
        return Optional.ofNullable(leader.get());
    }

    public void start() {
        leaderRefresher.connect(LEADERSHIP_OBSERVER_HANDLER, this::refresh);
        refresh(new Date());
    }

    public void stop() {
        leaderRefresher.disconnect(LEADERSHIP_OBSERVER_HANDLER);
        leaderRefresher.close();
    }

    private void refresh(Date currDate) {
        val leaderNode = nodeDataStore.nodes(NodeType.CONTROLLER)
                .stream()
                .map(nodeData -> nodeData.accept(new NodeDataVisitor<ControllerNodeData>() {
                    @Override
                    public ControllerNodeData visit(ControllerNodeData controllerData) {
                        return controllerData.isLeader()
                               ? controllerData
                               : null;
                    }

                    @Override
                    public ControllerNodeData visit(ExecutorNodeData executorData) {
                        return null;
                    }
                }))
                .filter(Objects::nonNull)
                .findAny()
                .orElse(null);
        log.trace("Leader node: {}", leaderNode);
        val oldLeader = leader.getAndSet(leaderNode);
        if (null != leaderNode) {
            if (null == oldLeader) {
                log.info("Leader set to: {}", leaderNode);
            }
            else if (!leaderNode.getHostname().equals(oldLeader.getHostname())) {
                log.info("Leader controller changed from: {} to: {}",
                         oldLeader, leaderNode);

            }
        }
    }
}
