package com.phonepe.drove.executor.discovery;

import com.google.common.base.Strings;
import com.phonepe.drove.common.CommonUtils;
import com.phonepe.drove.common.discovery.NodeDataStore;
import com.phonepe.drove.executor.utils.ExecutorUtils;
import com.phonepe.drove.executor.engine.InstanceEngine;
import com.phonepe.drove.executor.managed.ExecutorIdManager;
import com.phonepe.drove.executor.resourcemgmt.ResourceConfig;
import com.phonepe.drove.executor.resourcemgmt.ResourceManager;
import com.phonepe.drove.executor.resourcemgmt.ResourceInfo;
import com.phonepe.drove.executor.statemachine.BlacklistingManager;
import com.phonepe.drove.models.info.nodedata.ExecutorNodeData;
import com.phonepe.drove.models.info.nodedata.NodeTransportType;
import io.appform.signals.signals.ScheduledSignal;
import io.dropwizard.lifecycle.Managed;
import io.dropwizard.lifecycle.ServerLifecycleListener;
import io.dropwizard.setup.Environment;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.eclipse.jetty.server.Server;
import ru.vyarus.dropwizard.guice.module.installer.order.Order;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.time.Duration;
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
public class NodeDataUpdater implements Managed, ServerLifecycleListener {
    private final ExecutorIdManager executorIdManager;
    private final NodeDataStore nodeDataStore;
    private final ResourceManager resourceDB;
    private final InstanceEngine engine;
    private final ResourceConfig resourceConfig;
    private final BlacklistingManager blacklistingManager;

    private final ScheduledSignal refreshSignal = new ScheduledSignal(Duration.ofSeconds(10));
    private final AtomicBoolean started = new AtomicBoolean();
    private ExecutorNodeData currentData;
    private final Lock stateLock = new ReentrantLock();

    @Inject
    public NodeDataUpdater(
            ExecutorIdManager executorIdManager,
            NodeDataStore nodeDataStore,
            ResourceManager resourceDB,
            Environment environment,
            InstanceEngine engine,
            ResourceConfig resourceConfig,
            BlacklistingManager blacklistingManager) {
        this.executorIdManager = executorIdManager;
        this.nodeDataStore = nodeDataStore;
        this.resourceDB = resourceDB;
        this.engine = engine;
        this.resourceConfig = resourceConfig;
        this.blacklistingManager = blacklistingManager;
        this.refreshSignal.connect(this::refresh);
        this.engine.onStateChange().connect(info -> refresh(new Date()));
        environment.lifecycle().addServerLifecycleListener(this);
    }

    @Override
    public void start() throws Exception {
        resourceDB.onResourceUpdated().connect(this::refreshNodeState);
        blacklistingManager.onStateChange().connect(b -> refreshNodeState());
    }

    @Override
    public void stop() throws Exception {
        refreshSignal.close();
    }

    @Override
    public void serverStarted(Server server) {
        val port = getLocalPort(server);
        val hostname = CommonUtils.hostname();
        val cf = server.getConnectors()[0].getConnectionFactory("ssl");
        refreshNodeState(port,
                         null == cf
                         ? NodeTransportType.HTTP
                         : NodeTransportType.HTTPS,
                         hostname);
        started.set(true);
        log.info("Server started. Will start publishing node data");
    }

    private void refresh(Date currDate) {
        if (!started.get()) {
            log.warn("Node not started yet. Skipping state update");
        }
        refreshNodeState();
    }

    private void refreshNodeState(int port, NodeTransportType transportType, String hostname) {
        val resourceState = resourceDB.currentState();
        try {
            stateLock.lock();
            val executorId = executorIdManager.executorId().orElseGet(() -> CommonUtils.executorId(port));
            currentData = new ExecutorNodeData(
                    hostname,
                    port,
                    transportType,
                    new Date(),
                    ExecutorUtils.executorSnapshot(resourceState, executorId),
                    engine.currentState(),
                    tags(),
                    blacklistingManager.isBlacklisted());
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
                    engine.currentState(),
                    tags(),
                    blacklistingManager.isBlacklisted());
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
