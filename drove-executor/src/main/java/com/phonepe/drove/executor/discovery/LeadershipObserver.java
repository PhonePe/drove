package com.phonepe.drove.executor.discovery;

import com.phonepe.drove.common.discovery.NodeDataStore;
import com.phonepe.drove.models.info.nodedata.ControllerNodeData;
import com.phonepe.drove.models.info.nodedata.ExecutorNodeData;
import com.phonepe.drove.models.info.nodedata.NodeDataVisitor;
import com.phonepe.drove.models.info.nodedata.NodeType;
import io.appform.signals.signals.ScheduledSignal;
import io.dropwizard.lifecycle.Managed;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import ru.vyarus.dropwizard.guice.module.installer.order.Order;

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
@Order(30)
@Slf4j
@Singleton
public class LeadershipObserver implements Managed {

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

    @Override
    public void start() throws Exception {
        leaderRefresher.connect(this::refresh);
        refresh(new Date());
    }

    @Override
    public void stop() throws Exception {
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
