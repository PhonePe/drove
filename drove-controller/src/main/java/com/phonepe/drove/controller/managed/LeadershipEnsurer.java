package com.phonepe.drove.controller.managed;

import com.google.common.annotations.VisibleForTesting;
import com.phonepe.drove.common.CommonUtils;
import com.phonepe.drove.common.discovery.NodeDataStore;
import com.phonepe.drove.controller.event.DroveEventBus;
import com.phonepe.drove.controller.event.DroveEventType;
import com.phonepe.drove.controller.event.events.DroveClusterEvent;
import com.phonepe.drove.controller.utils.EventUtils;
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
    private final DroveEventBus eventBus;
    private final LeaderLatch leaderLatch;
    private final ScheduledSignal checkLeadership = new ScheduledSignal(Duration.ofSeconds(60));
    private final ConsumingSyncSignal<Boolean> leadershipStateChanged = new ConsumingSyncSignal<>();
    private final Lock stateLock = new ReentrantLock();
    private final AtomicBoolean started = new AtomicBoolean();
    private final AtomicBoolean connected = new AtomicBoolean(true);
    private final AtomicBoolean leader = new AtomicBoolean();

    private ControllerNodeData currentData;

    @SuppressWarnings("java:S1075")
    @Inject
    public LeadershipEnsurer(
            CuratorFramework curatorFramework,
            NodeDataStore nodeDataStore,
            Environment environment,
            DroveEventBus eventBus) {
        this.nodeDataStore = nodeDataStore;
        this.eventBus = eventBus;
        val path = "/leadership";

        this.leaderLatch = new LeaderLatch(curatorFramework, path);
        val leaderLatchListener = new LeaderLatchListener() {
            @Override
            public void isLeader() {
                log.info("This node became leader. Updating state.");
                refreshNodeState();
            }

            @Override
            public void notLeader() {
                log.info("This node lost leadership. Updating state.");
                refreshNodeState();
            }
        };
        curatorFramework.getConnectionStateListenable().addListener((client, newState) -> {
            log.info("Zk connection state changed to: {}", newState);
            switch (newState) {
                case CONNECTED, RECONNECTED -> {
                    connected.set(true);
                    log.info("Node is connected to Zk");
                }
                case SUSPENDED, LOST, READ_ONLY -> {
                    connected.set(false);
                    log.info("Node is disconnected from Zk");
                }
            }
        });
        this.leaderLatch.addListener(leaderLatchListener);
        this.checkLeadership.connect(this::refresh);
        environment.lifecycle().addServerLifecycleListener(this);
    }

    @Override
    public void start() throws Exception {
        //Nothing to do here. Latch will be started once server starts listening
    }

    @Override
    public void stop() throws Exception {
        log.debug("Shutting down {}", this.getClass().getSimpleName());
        if(started.get()) {
            leaderLatch.close();
        }
        started.set(false);
        log.debug("Shut down {}", this.getClass().getSimpleName());
    }

    public ConsumingSyncSignal<Boolean> onLeadershipStateChanged() {
        return leadershipStateChanged;
    }

    public boolean isLeader() {
        return leader.get();
    }

    @Override
    @VisibleForTesting
    public void serverStarted(Server server) {
        val cf = server.getConnectors()[0].getConnectionFactory("ssl");

        refreshNodeState(getLocalPort(server),
                         cf == null
                         ? NodeTransportType.HTTP
                         : NodeTransportType.HTTPS,
                         CommonUtils.hostname());
    }

    private void refresh(Date currDate) {
        refreshNodeState();
        log.trace("ZK State update for controller at: {}", currDate);
    }

    private void refreshNodeState(int port, NodeTransportType transportType, String hostname) {
        val currLeadershipState = connected.get() && this.leaderLatch.hasLeadership();
        val oldState = leader.getAndSet(currLeadershipState);
        stateLock.lock();
        try {
            currentData = new ControllerNodeData(hostname, port, transportType, new Date(), currLeadershipState);
            nodeDataStore.updateNodeData(currentData);
            log.info("Node created for this controller. Data: {}", currentData);
            leaderLatch.start();
            log.info("Node started participating in leader election");
            started.set(true);
        }
        catch (Exception e) {
            log.error("Error in state update: " + e.getMessage(), e);
        }
        finally {
            stateLock.unlock();
        }
        handleLeadershipUpdate(currLeadershipState, oldState);
    }

    private void refreshNodeState() {
        if (!started.get()) {
            log.error("Ignoring ZK controller data update as server has not started completely");
            return;
        }
        val currLeadershipState = connected.get() && this.leaderLatch.hasLeadership();
        val oldState = leader.getAndSet(currLeadershipState);

        stateLock.lock();
        try {
            currentData = ControllerNodeData.from(currentData, currLeadershipState);
            nodeDataStore.updateNodeData(currentData);
        }
        catch (Exception e) {
            log.error("Error in state update: " + e.getMessage(), e);
        }
        finally {
            stateLock.unlock();
        }
        log.trace("Curr state: {} Old State: {}", currLeadershipState, oldState);
        handleLeadershipUpdate(currLeadershipState, oldState);
    }

    private void handleLeadershipUpdate(boolean currLeadershipState, boolean oldState) {
        if(currLeadershipState != oldState) {
            if(currLeadershipState) {
                log.info("This node became leader");
            }
            else {
                log.info("This node lost leadership");
            }
            eventBus.publish(new DroveClusterEvent(
                    currLeadershipState
                    ? DroveEventType.LEADERSHIP_ACQUIRED
                    : DroveEventType.LEADERSHIP_LOST,
                    EventUtils.controllerMetadata()));
            leadershipStateChanged.dispatch(currLeadershipState);
        }
    }
}
