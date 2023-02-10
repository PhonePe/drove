package com.phonepe.drove.controller.managed;

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
import org.apache.curator.framework.recipes.leader.LeaderSelector;
import org.apache.curator.framework.recipes.leader.LeaderSelectorListenerAdapter;
import org.eclipse.jetty.server.Server;
import ru.vyarus.dropwizard.guice.module.installer.order.Order;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.time.Duration;
import java.util.Date;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Condition;
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
    private final LeaderSelector leaderLatch;
    private final ScheduledSignal checkLeadership = new ScheduledSignal(Duration.ofSeconds(60));
    private final ConsumingSyncSignal<Boolean> leadershipStateChanged = new ConsumingSyncSignal<>();
    private final Lock stateLock = new ReentrantLock();
    private final AtomicBoolean started = new AtomicBoolean();
    private final AtomicBoolean leader = new AtomicBoolean();
    private final ControllerLeadershipListener listener;

    private ControllerNodeData currentData;

    private final class ControllerLeadershipListener extends LeaderSelectorListenerAdapter {

        private final AtomicBoolean stopped = new AtomicBoolean();
        private final Lock stopLock = new ReentrantLock();
        private final Condition stopCondition = stopLock.newCondition();

        @Override
        public void takeLeadership(CuratorFramework client) throws Exception {
            log.info("Acquired leadership for this node.");
            leader.set(true);
            refreshNodeState();
            handleLeadershipUpdate(true, false);
            stopLock.lock();
            try {
                while (!stopped.get()) {
                    stopCondition.await();
                }
            }
            catch (InterruptedException e) {
                log.warn("Leadership interrupted...");
                Thread.currentThread().interrupt();
            }
            finally {
                stopLock.unlock();
            }
            leader.set(false);
            refreshNodeState();
            handleLeadershipUpdate(false, true);
            log.info("Relinquished leadership for this node");
        }

        public void stop() {
            stopLock.lock();
            try {
                stopped.set(true);
                stopCondition.signalAll();
            }
            finally {
                stopLock.unlock();
            }
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

    @SuppressWarnings("java:S1075")
    @Inject
    public LeadershipEnsurer(
            CuratorFramework curatorFramework,
            NodeDataStore nodeDataStore,
            Environment environment,
            DroveEventBus eventBus) {
        this.nodeDataStore = nodeDataStore;
        this.eventBus = eventBus;
        this.listener = new ControllerLeadershipListener();
        val path = "/leaderselection";

        this.leaderLatch = new LeaderSelector(curatorFramework, path, listener);
        leaderLatch.autoRequeue();
        this.checkLeadership.connect(this::refresh);
        environment.lifecycle().addServerLifecycleListener(this);
    }

    @Override
    public void start() throws Exception {
        //Nothing to do here. Selector will be started once server starts listening
    }

    @Override
    public void stop() throws Exception {
        log.debug("Shutting down {}", this.getClass().getSimpleName());
        if(started.get()) {
            listener.stop();
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
        stateLock.lock();
        try {
            currentData = new ControllerNodeData(hostname, port, transportType, new Date(), isLeader());
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
    }

    private void refreshNodeState() {
        if (!started.get()) {
            log.error("Ignoring ZK controller data update as server has not started completely");
            return;
        }
        stateLock.lock();
        try {
            currentData = ControllerNodeData.from(currentData, isLeader());
            nodeDataStore.updateNodeData(currentData);
        }
        catch (Exception e) {
            log.error("Error in state update: " + e.getMessage(), e);
        }
        finally {
            stateLock.unlock();
        }
    }
}
