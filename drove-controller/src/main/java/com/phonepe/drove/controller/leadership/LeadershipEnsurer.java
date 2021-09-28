package com.phonepe.drove.controller.leadership;

import com.phonepe.drove.common.CommonUtils;
import com.phonepe.drove.common.discovery.NodeDataStore;
import com.phonepe.drove.common.discovery.nodedata.ControllerNodeData;
import com.phonepe.drove.common.zookeeper.ZkConfig;
import io.appform.signals.signals.ScheduledSignal;
import io.dropwizard.lifecycle.Managed;
import io.dropwizard.lifecycle.ServerLifecycleListener;
import io.dropwizard.setup.Environment;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.recipes.leader.LeaderLatch;
import org.apache.curator.framework.recipes.leader.LeaderLatchListener;
import org.eclipse.jetty.server.Server;
import ru.vyarus.dropwizard.guice.module.installer.order.Order;

import javax.inject.Inject;
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
@Order(10)
public class LeadershipEnsurer implements Managed, ServerLifecycleListener {
    private final CuratorFramework curatorFramework;
    private final ZkConfig config;
    private final NodeDataStore nodeDataStore;
    private final LeaderLatch leaderLatch;
    private final ScheduledSignal checkLeadership = new ScheduledSignal(Duration.ofSeconds(60));
    private final String nodeId = UUID.randomUUID().toString();
    private final Lock stateLock = new ReentrantLock();
    private final AtomicBoolean started = new AtomicBoolean();
    private ControllerNodeData currentData;

    @Inject
    public LeadershipEnsurer(CuratorFramework curatorFramework, ZkConfig config, NodeDataStore nodeDataStore, Environment environment) {
        this.curatorFramework = curatorFramework;
        this.config = config;
        this.nodeDataStore = nodeDataStore;
        val path = "/leadership";
        this.leaderLatch = new LeaderLatch(curatorFramework, path);
        this.leaderLatch.addListener(new LeaderLatchListener() {
            @Override
            public void isLeader() {
                log.info("This node because leader. Updating state.");
                refreshNodeState();
            }

            @Override
            public void notLeader() {
                log.info("This node lost leadership. Updating state.");
                refreshNodeState();
            }
        });
        this.checkLeadership.connect(this::refresh);
        environment.lifecycle().addServerLifecycleListener(this);
    }

    @Override
    public void start() throws Exception {
        leaderLatch.start();
    }

    @Override
    public void stop() throws Exception {
        leaderLatch.close();
    }

    public boolean isLeader() {
        return this.leaderLatch.hasLeadership();
    }

    @Override
    public void serverStarted(Server server) {
        refreshNodeState(getLocalPort(server), CommonUtils.hostname(config.getHostname()));
    }

    private void refresh(Date currDate) {
        if (!started.get()) {
            log.warn("Node not started yet. Skipping state update");
        }
        refreshNodeState();
    }

    private void refreshNodeState(int port, String hostname) {
        try {
            stateLock.lock();
            currentData = new ControllerNodeData(hostname, port, new Date(), isLeader());
            nodeDataStore.updateNodeData(currentData);
            started.set(true);
            log.info("Node created for this controller. Data: {}", currentData);
        } finally {
            stateLock.unlock();
        }
    }

    private void refreshNodeState() {
        try {
            stateLock.lock();
            if(null == currentData) {
                log.error("Ignoring update as server has not started");
                return;
            }
            currentData = ControllerNodeData.from(currentData, isLeader());
            nodeDataStore.updateNodeData(currentData);
        } finally {
            stateLock.unlock();
        }
    }
}
