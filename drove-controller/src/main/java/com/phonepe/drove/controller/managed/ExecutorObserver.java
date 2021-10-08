package com.phonepe.drove.controller.managed;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.phonepe.drove.common.discovery.nodedata.ExecutorNodeData;
import com.phonepe.drove.common.zookeeper.ZkUtils;
import com.phonepe.drove.controller.engine.StateUpdater;
import io.appform.signals.signals.ScheduledSignal;
import io.dropwizard.lifecycle.Managed;
import lombok.extern.slf4j.Slf4j;
import org.apache.curator.framework.CuratorFramework;
import org.apache.zookeeper.KeeperException;
import ru.vyarus.dropwizard.guice.module.installer.order.Order;

import javax.inject.Inject;
import java.time.Duration;
import java.util.Date;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 *
 */
@Slf4j
@Order(20)
public class ExecutorObserver implements Managed {

    private final CuratorFramework curatorFramework;
    private final ObjectMapper mapper;
    private final StateUpdater updater;
    private final Lock refreshLock = new ReentrantLock();
    private final ScheduledSignal dataRefresher = new ScheduledSignal(Duration.ofSeconds(30));

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
                updater.updateClusterResources(ZkUtils.readChildrenNodes(curatorFramework,
                                                          "/executor",
                                                          0,
                                                          Integer.MAX_VALUE,
                                                          path -> ZkUtils.readNodeData(curatorFramework,
                                                                                       "/executor/" + path,
                                                                                       mapper,
                                                                                       ExecutorNodeData.class)));
                log.info("Completed refresh for invocation call at: {}", currentDate);
            }
            catch (KeeperException.NoNodeException e) {
                log.warn("No executors found.. Maybe executors not started?");
            }
            catch (Exception e) {
                log.error("Error reading children from ZK: ", e);
            }
            finally {
                refreshLock.unlock();
            }
        }
        else {
            log.warn("Looks like ZK reads are slow, skipping this data load.");
        }
    }
}
