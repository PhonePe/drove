package com.phonepe.drove.controller.managed;

import com.phonepe.drove.common.CommonUtils;
import com.phonepe.drove.common.discovery.NodeDataStore;
import com.phonepe.drove.models.info.nodedata.ControllerNodeData;
import com.phonepe.drove.models.info.nodedata.NodeTransportType;
import io.appform.signals.signals.ConsumingSyncSignal;
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
import javax.inject.Singleton;
import java.time.Duration;
import java.util.Date;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 *
 */
@Slf4j
@Order(10)
@Singleton
public class LeadershipEnsurer implements Managed, ServerLifecycleListener {
    private final NodeDataStore nodeDataStore;
    private final LeaderLatch leaderLatch;
    private final ScheduledSignal checkLeadership = new ScheduledSignal(Duration.ofSeconds(60));
    private final ConsumingSyncSignal<Boolean> leadershipStateChanged = new ConsumingSyncSignal<>();
    private final Lock stateLock = new ReentrantLock();
    private final AtomicBoolean started = new AtomicBoolean();
    private ControllerNodeData currentData;

    @Inject
    public LeadershipEnsurer(
            CuratorFramework curatorFramework,
            NodeDataStore nodeDataStore,
            Environment environment) {
        this.nodeDataStore = nodeDataStore;
        val path = "/leadership";
        this.leaderLatch = new LeaderLatch(curatorFramework, path);
        this.leaderLatch.addListener(new LeaderLatchListener() {
            @Override
            public void isLeader() {
                log.info("This node became leader. Updating state.");
                refreshNodeState();
                leadershipStateChanged.dispatch(true);
            }

            @Override
            public void notLeader() {
                log.info("This node lost leadership. Updating state.");
                refreshNodeState();
                leadershipStateChanged.dispatch(false);
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
        log.debug("Shutting down {}", this.getClass().getSimpleName());
        leaderLatch.close();
        log.debug("Shut down {}", this.getClass().getSimpleName());
    }

    public ConsumingSyncSignal<Boolean> onLeadershipStateChanged() {
        return leadershipStateChanged;
    }

    public boolean isLeader() {
        return this.leaderLatch.hasLeadership();
    }

    @Override
    public void serverStarted(Server server) {
        val cf = server.getConnectors()[0].getConnectionFactory("ssl");

        refreshNodeState(getLocalPort(server),
                         cf == null
                         ? NodeTransportType.HTTP
                         : NodeTransportType.HTTPS,
                         CommonUtils.hostname());
    }

    private void refresh(Date currDate) {
        if (!started.get()) {
            log.warn("Node not started yet. Skipping state update");
        }
        refreshNodeState();
    }

    private void refreshNodeState(int port, NodeTransportType transportType, String hostname) {
        try {
            stateLock.lock();
            currentData = new ControllerNodeData(hostname, port, transportType, new Date(), isLeader());
            nodeDataStore.updateNodeData(currentData);
            started.set(true);
            log.info("Node created for this controller. Data: {}", currentData);
        }
        finally {
            stateLock.unlock();
        }
    }

    private void refreshNodeState() {
        try {
            stateLock.lock();
            if (null == currentData) {
                log.error("Ignoring update as server has not started");
                return;
            }
            currentData = ControllerNodeData.from(currentData, isLeader());
            nodeDataStore.updateNodeData(currentData);
        }
        finally {
            stateLock.unlock();
        }
    }
}
