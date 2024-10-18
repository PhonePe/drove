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

package com.phonepe.drove.executor.discovery;

import com.google.common.base.Strings;
import com.phonepe.drove.common.CommonUtils;
import com.phonepe.drove.common.discovery.Constants;
import com.phonepe.drove.common.discovery.NodeDataStore;
import com.phonepe.drove.executor.engine.ApplicationInstanceEngine;
import com.phonepe.drove.executor.engine.LocalServiceInstanceEngine;
import com.phonepe.drove.executor.engine.TaskInstanceEngine;
import com.phonepe.drove.executor.managed.ExecutorIdManager;
import com.phonepe.drove.executor.resourcemgmt.ResourceConfig;
import com.phonepe.drove.executor.resourcemgmt.ResourceInfo;
import com.phonepe.drove.executor.resourcemgmt.ResourceManager;
import com.phonepe.drove.executor.statemachine.ExecutorStateManager;
import com.phonepe.drove.executor.utils.ExecutorUtils;
import com.phonepe.drove.models.info.nodedata.ExecutorNodeData;
import com.phonepe.drove.models.info.nodedata.NodeTransportType;
import io.appform.signals.signals.ScheduledSignal;
import io.dropwizard.lifecycle.Managed;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import ru.vyarus.dropwizard.guice.module.installer.order.Order;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.time.Instant;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 *
 */
@Slf4j
@Singleton
@Order(40)
public class NodeDataUpdater implements Managed {
    private final NodeDataStore nodeDataStore;
    private final ResourceManager resourceDB;
    private final ApplicationInstanceEngine applicationInstanceEngine;
    private final TaskInstanceEngine taskInstanceEngine;
    private final LocalServiceInstanceEngine localServiceInstanceEngine;
    private final ResourceConfig resourceConfig;
    private final ExecutorStateManager executorStateManager;

    private final ScheduledSignal refreshSignal = new ScheduledSignal(Constants.EXECUTOR_REFRESH_INTERVAL);
    private final AtomicBoolean started = new AtomicBoolean();
    private ExecutorNodeData currentData;
    private final Lock stateLock = new ReentrantLock();

    @Inject
    public NodeDataUpdater(
            ExecutorIdManager executorIdManager,
            NodeDataStore nodeDataStore,
            ResourceManager resourceDB,
            ApplicationInstanceEngine applicationInstanceEngine,
            TaskInstanceEngine taskInstanceEngine, LocalServiceInstanceEngine localServiceInstanceEngine,
            ResourceConfig resourceConfig,
            ExecutorStateManager executorStateManager) {
        this.nodeDataStore = nodeDataStore;
        this.resourceDB = resourceDB;
        this.applicationInstanceEngine = applicationInstanceEngine;
        this.taskInstanceEngine = taskInstanceEngine;
        this.localServiceInstanceEngine = localServiceInstanceEngine;
        this.resourceConfig = resourceConfig;
        this.executorStateManager = executorStateManager;
        this.refreshSignal.connect(this::refresh);
        this.applicationInstanceEngine.onStateChange().connect(info -> refresh(new Date()));
        executorIdManager.onHostInfoGenerated()
                .connect(this::hostInfoAvailable);
    }

    @Override
    public void start() throws Exception {
        resourceDB.onResourceUpdated().connect(this::refreshNodeState);
        executorStateManager.onStateChange().connect(state -> refreshNodeState());
    }

    @Override
    public void stop() throws Exception {
        refreshSignal.close();
    }


    public void hostInfoAvailable(ExecutorIdManager.ExecutorHostInfo hostInfo) {
        refreshNodeState(hostInfo.getPort(),
                         hostInfo.getTransportType(),
                         hostInfo.getHostname(),
                         hostInfo.getExecutorId());
        started.set(true);
        log.info("Server started. Will start publishing node data");
    }

    private void refresh(Date currDate) {
        if (!started.get()) {
            log.warn("Node not started yet. Skipping state update");
        }
        refreshNodeState();
        log.info("Node data updated at: {}", Instant.now());
    }

    private void refreshNodeState(int port, NodeTransportType transportType, String hostname, String executorId) {
        val resourceState = resourceDB.currentState();
        try {
            stateLock.lock();
            currentData = new ExecutorNodeData(
                    hostname,
                    port,
                    transportType,
                    new Date(),
                    ExecutorUtils.executorSnapshot(resourceState, executorId),
                    applicationInstanceEngine.currentState(),
                    taskInstanceEngine.currentState(),
                    localServiceInstanceEngine.currentState(),
                    tags(),
                    executorStateManager.currentState());
            nodeDataStore.updateNodeData(currentData);
        }
        finally {
            stateLock.unlock();
        }
    }

    private void refreshNodeState() {
        val resourceState = resourceDB.currentState();
        refreshNodeState(resourceState);
    }

    private void refreshNodeState(ResourceInfo resourceState) {
        try {
            stateLock.lock();
            if (!started.get()) {
                log.warn("Node is not started no data is updated in store.");
                return;
            }
            currentData = ExecutorNodeData.from(
                    currentData,
                    ExecutorUtils.executorSnapshot(resourceState, currentData.getState().getExecutorId()),
                    applicationInstanceEngine.currentState(),
                    taskInstanceEngine.currentState(),
                    localServiceInstanceEngine.currentState(),
                    tags(),
                    executorStateManager.currentState());
            nodeDataStore.updateNodeData(currentData);
        }
        finally {
            stateLock.unlock();
        }
    }

    private Set<String> tags() {
        val hostname = currentData != null && !Strings.isNullOrEmpty(currentData.getHostname())
                       ? currentData.getHostname()
                       : CommonUtils.hostname();
        val existing = new HashSet<>(resourceConfig.getTags() == null
                                     ? Collections.emptySet()
                                     : resourceConfig.getTags());
        existing.add(hostname);
        return Set.copyOf(existing);
    }

}
