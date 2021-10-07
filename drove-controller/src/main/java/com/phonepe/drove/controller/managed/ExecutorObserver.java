package com.phonepe.drove.controller.managed;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.phonepe.drove.common.discovery.nodedata.ExecutorNodeData;
import com.phonepe.drove.common.zookeeper.ZkUtils;
import com.phonepe.drove.controller.resources.ClusterResourcesDB;
import com.phonepe.drove.controller.statedb.ApplicationStateDB;
import io.appform.signals.signals.ScheduledSignal;
import io.dropwizard.lifecycle.Managed;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.apache.curator.framework.CuratorFramework;
import org.apache.zookeeper.KeeperException;
import ru.vyarus.dropwizard.guice.module.installer.order.Order;

import javax.inject.Inject;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Date;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 *
 */
@Slf4j
@Order(20)
public class ExecutorObserver implements Managed {
    private final ClusterResourcesDB resourcesDB;
    private final ApplicationStateDB applicationStateDB;
    private final CuratorFramework curatorFramework;
    private final ObjectMapper mapper;
    private final Lock refreshLock = new ReentrantLock();
    private final ScheduledSignal dataRefresher = new ScheduledSignal(Duration.ofSeconds(1));

    @Inject
    public ExecutorObserver(
            ClusterResourcesDB resourcesDB,
            ApplicationStateDB applicationStateDB,
            CuratorFramework curatorFramework, ObjectMapper mapper) {
        this.resourcesDB = resourcesDB;
        this.applicationStateDB = applicationStateDB;
        this.curatorFramework = curatorFramework;
        this.mapper = mapper;
    }

    @Override
    public void start() throws Exception {
        dataRefresher.connect(this::refreshDataFromZK);
    }

    @Override
    public void stop() throws Exception {
        dataRefresher.close();
    }

    private void refreshDataFromZK(final Date currentDate) {
        val children = new ArrayList<ExecutorNodeData>();
        if (refreshLock.tryLock()) {
            try {
                children.addAll(ZkUtils.readChildrenNodes(curatorFramework,
                                                          "/executor",
                                                          0,
                                                          Integer.MAX_VALUE,
                                                          path -> ZkUtils.readNodeData(curatorFramework,
                                                                                       "/executor/" + path,
                                                                                       mapper,
                                                                                       ExecutorNodeData.class)));
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
        if (children.isEmpty()) {
            log.warn("No children found from ZK.");
            return;
        }
        resourcesDB.update(children);
    }
}
