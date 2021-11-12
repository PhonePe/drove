package com.phonepe.drove.controller.managed;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Sets;
import com.phonepe.drove.common.discovery.nodedata.ExecutorNodeData;
import com.phonepe.drove.common.zookeeper.ZkUtils;
import com.phonepe.drove.controller.engine.StateUpdater;
import io.appform.signals.signals.ScheduledSignal;
import io.dropwizard.lifecycle.Managed;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.apache.curator.framework.CuratorFramework;
import org.apache.zookeeper.KeeperException;
import ru.vyarus.dropwizard.guice.module.installer.order.Order;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

/**
 *
 */
@Slf4j
@Order(20)
@Singleton
public class ExecutorObserver implements Managed {

    private final CuratorFramework curatorFramework;
    private final ObjectMapper mapper;
    private final StateUpdater updater;
    private final Lock refreshLock = new ReentrantLock();
    private final ScheduledSignal dataRefresher = new ScheduledSignal(Duration.ofSeconds(30));
    private final Set<String> knownExecutors = new HashSet<>();

    @Inject
    public ExecutorObserver(CuratorFramework curatorFramework, ObjectMapper mapper, StateUpdater updater) {
        this.curatorFramework = curatorFramework;
        this.mapper = mapper;
        this.updater = updater;
    }

    @Override
    public void start() throws Exception {
        refreshDataFromZK(new Date());
        dataRefresher.connect(this::refreshDataFromZK);
    }

    @Override
    public void stop() throws Exception {
        dataRefresher.close();
    }

    private void refreshDataFromZK(final Date currentDate) {
        if (refreshLock.tryLock()) {
            try {
                val currentExecutors = fetchNodes();
                val ids = currentExecutors.stream()
                        .map(n -> n.getState().getExecutorId())
                        .collect(Collectors.toUnmodifiableSet());
                if(knownExecutors.equals(ids)) {
                    log.info("No changes detected in cluster topology");
                }
                else {
                    val missingExecutors = Sets.difference(knownExecutors, ids);
                    if(!missingExecutors.isEmpty()) {
                        log.info("Missing executors detected: {}", missingExecutors);
                        updater.remove(missingExecutors);
                    }
                    else {
                        val newExecutors = Sets.difference(ids, knownExecutors);
                        log.info("New executors detected: {}", newExecutors);
                    }
                    knownExecutors.clear();
                    knownExecutors.addAll(ids);
                }
                updater.updateClusterResources(currentExecutors);
                log.info("Completed refresh for invocation call at: {}", currentDate);
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
        try {
            return ZkUtils.readChildrenNodes(curatorFramework,
                                             "/executor",
                                             0,
                                             Integer.MAX_VALUE,
                                             path -> ZkUtils.readNodeData(curatorFramework,
                                                                          "/executor/" + path,
                                                                          mapper,
                                                                          ExecutorNodeData.class));
        }
        catch (KeeperException.NoNodeException e) {
            log.warn("No executors found.. Maybe executors not started?");
        }
        catch (Exception e) {
            log.error("Error reading children from ZK: ", e);
        }
        return Collections.emptyList();
    }
}
