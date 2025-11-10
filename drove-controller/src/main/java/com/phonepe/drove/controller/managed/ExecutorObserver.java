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

package com.phonepe.drove.controller.managed;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Sets;
import com.phonepe.drove.common.coverageutils.IgnoreInJacocoGeneratedReport;
import com.phonepe.drove.common.discovery.NodeDataStore;
import com.phonepe.drove.controller.config.ControllerOptions;
import com.phonepe.drove.controller.engine.StateUpdater;
import com.phonepe.drove.controller.event.DroveEventBus;
import com.phonepe.drove.models.events.events.DroveExecutorAddedEvent;
import com.phonepe.drove.models.events.events.DroveExecutorRemovedEvent;
import com.phonepe.drove.models.info.nodedata.ExecutorNodeData;
import com.phonepe.drove.models.info.nodedata.NodeType;
import io.appform.signals.signals.ScheduledSignal;
import io.dropwizard.lifecycle.Managed;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import ru.vyarus.dropwizard.guice.module.installer.order.Order;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

import static com.phonepe.drove.controller.utils.EventUtils.executorMetadata;

/**
 * The ExecutorObserver keeps track of executors in the cluster by querying the raw data from the {@link NodeDataStore}
 * and keeps the in memory cluster resources db uptodate by comparing current and previous states. Any executor which
 * has _not_ updated it's data in the stipulated window is considered to have been removed from the cluster.
 */
@Slf4j
@Order(20)
@Singleton
public class ExecutorObserver implements Managed {

    private final NodeDataStore nodeDataStore;
    private final StateUpdater updater;
    private final LeadershipEnsurer leadershipEnsurer;
    private final DroveEventBus eventBus;
    private final Duration staleExecutorAge;
    private final Lock refreshLock = new ReentrantLock();
    private final ScheduledSignal dataRefresher;
    private final Set<String> knownExecutors = new HashSet<>();

    @Inject
    @IgnoreInJacocoGeneratedReport
    public ExecutorObserver(
            NodeDataStore nodeDataStore,
            StateUpdater updater,
            LeadershipEnsurer leadershipEnsurer,
            DroveEventBus eventBus,
            ControllerOptions controllerOptions) {
        this(nodeDataStore,
             updater,
             leadershipEnsurer,
             eventBus,
             controllerOptions,
             Duration.ofSeconds(10));
    }

    @VisibleForTesting
    ExecutorObserver(
            NodeDataStore nodeDataStore,
            StateUpdater updater,
            LeadershipEnsurer leadershipEnsurer,
            DroveEventBus eventBus,
            ControllerOptions controllerOptions,
            Duration dataRefreshInterval) {
        this.nodeDataStore = nodeDataStore;
        this.updater = updater;
        this.leadershipEnsurer = leadershipEnsurer;
        this.eventBus = eventBus;
        this.staleExecutorAge = Objects.requireNonNullElse(controllerOptions.getStaleExecutorAge(),
                                                           ControllerOptions.DEFAULT_STALE_EXECUTOR_AGE)
                .toJavaDuration();
        this.dataRefresher = new ScheduledSignal(dataRefreshInterval);
    }

    @Override
    public void start() throws Exception {
        dataRefresher.connect(this::refreshDataFromZK);
        leadershipEnsurer.onLeadershipStateChanged()
                .connect(leader -> {
                    if (Boolean.TRUE.equals(leader)) {
                        log.info("Became leader, doing an emergency update to rebuild cluster metadata");
                        refreshDataFromZK(new Date());
                    }
                });
    }

    @Override
    public void stop() throws Exception {
        log.debug("Shutting down {}", this.getClass().getSimpleName());
        dataRefresher.close();
        log.debug("Shut down {}", this.getClass().getSimpleName());
    }

    private void refreshDataFromZK(final Date currentDate) {
        if (!leadershipEnsurer.isLeader()) {
            log.info("Skipping data refresh from zk as i'm not the leader");
            return;
        }
        if (refreshLock.tryLock()) {
            try {
                val currentExecutors = fetchNodes();
                val ids = currentExecutors.stream()
                        .map(n -> n.getState().getExecutorId())
                        .collect(Collectors.toUnmodifiableSet());
                if (knownExecutors.equals(ids)) {
                    log.trace("No changes detected in cluster topology");
                }
                else {
                    val missingExecutors = Sets.difference(knownExecutors, ids);
                    if (!missingExecutors.isEmpty()) {
                        log.info("Missing executors detected: {}", missingExecutors);
                        updater.remove(Set.copyOf(missingExecutors));
                        missingExecutors.forEach(
                                executorId -> eventBus.publish(
                                        new DroveExecutorRemovedEvent(executorMetadata(executorId))));
                    }
                    val newExecutors = Sets.difference(ids, knownExecutors);
                    if (!newExecutors.isEmpty()) {
                        log.info("New executors detected: {}", newExecutors);
                        currentExecutors.stream()
                                .filter(executor -> newExecutors.contains(executor.getState().getExecutorId()))
                                .forEach(executor -> eventBus.publish(
                                        new DroveExecutorAddedEvent(executorMetadata(executor))));
                    }
                    knownExecutors.clear();
                    knownExecutors.addAll(ids);
                }
                updater.updateClusterResources(currentExecutors);
                log.info("Cluster state refresh completed for invocation at: {}", currentDate);
            }
            finally {
                refreshLock.unlock();
            }
        }
        else {
            log.warn("Looks like ZK reads are slow, skipping this data load.");
        }
    }

    private List<ExecutorNodeData> fetchNodes() {
        val lastAllowed = Date.from(Instant.now().minus(staleExecutorAge));
        return nodeDataStore.nodes(NodeType.EXECUTOR)
                .stream()
                .map(ExecutorNodeData.class::cast)
                .filter(executor -> {
                    val stale = executor.getUpdated().before(lastAllowed);
                    if (stale) {
                        log.warn("Executor {} last updated was {}. check threshold is {}",
                                 executor.getState().getExecutorId(),
                                 executor.getUpdated(),
                                 lastAllowed);
                    }
                    return !stale;
                })
                .toList();
    }
}
