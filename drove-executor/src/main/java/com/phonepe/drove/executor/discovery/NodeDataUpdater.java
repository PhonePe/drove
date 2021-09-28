package com.phonepe.drove.executor.discovery;

import com.phonepe.drove.common.CommonUtils;
import com.phonepe.drove.common.discovery.NodeDataStore;
import com.phonepe.drove.common.zookeeper.ZkConfig;
import com.phonepe.drove.common.discovery.nodedata.ExecutorNodeData;
import com.phonepe.drove.common.model.ExecutorState;
import com.phonepe.drove.executor.resource.ResourceDB;
import com.phonepe.drove.executor.resource.ResourceInfo;
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
import java.util.Date;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 *
 */
@Slf4j
@Singleton
@Order(10)
public class NodeDataUpdater implements Managed, ServerLifecycleListener {
    private final ZkConfig config;
    private final NodeDataStore nodeDataStore;
    private final ResourceDB resourceDB;
    private final ScheduledSignal refreshSignal = new ScheduledSignal(Duration.ofSeconds(60));
    private final AtomicBoolean started = new AtomicBoolean();
    private ExecutorNodeData currentData;
    private final String nodeId = UUID.randomUUID().toString();
    private final Lock stateLock = new ReentrantLock();

    @Inject
    public NodeDataUpdater(
            ZkConfig config,
            NodeDataStore nodeDataStore,
            ResourceDB resourceDB,
            Environment environment) {
        this.config = config;
        this.nodeDataStore = nodeDataStore;
        this.resourceDB = resourceDB;
        this.refreshSignal.connect(this::refresh);
        environment.lifecycle().addServerLifecycleListener(this);
    }

    @Override
    public void start() throws Exception {
        resourceDB.onResourceUpdated().connect(this::refreshNodeState);
    }

    @Override
    public void stop() throws Exception {
        refreshSignal.close();
    }

    @Override
    public void serverStarted(Server server) {
        log.info("Server started. Will start publishing node data");
        val port = getLocalPort(server);
        val hostname = CommonUtils.hostname(config.getHostname());
        refreshNodeState(port, hostname);
        started.set(true);
    }

    private void refresh(Date currDate) {
        if (!started.get()) {
            log.warn("Node not started yet. Skipping state update");
        }
        refreshNodeState();
    }

    private void refreshNodeState(int port, String hostname) {
        val resourceState = resourceDB.currentState();
        try {
            stateLock.lock();
            currentData = new ExecutorNodeData(
                    hostname, port, new Date(),
                    new ExecutorState(nodeId, resourceState.getCpu(), resourceState.getMemory()));
            nodeDataStore.updateNodeData(currentData);
        } finally {
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
            currentData = ExecutorNodeData.from(currentData,
                                                new ExecutorState(nodeId,
                                                                  resourceState.getCpu(),
                                                                  resourceState.getMemory()));
            nodeDataStore.updateNodeData(currentData);
        } finally {
          stateLock.unlock();
        }
    }
}
