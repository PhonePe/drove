package com.phonepe.drove.executor.discovery;

import io.dropwizard.lifecycle.Managed;
import org.apache.curator.framework.CuratorFramework;
import ru.vyarus.dropwizard.guice.module.installer.order.Order;

import javax.inject.Inject;

/**
 *
 */
@Order(20)
public class ZookeeperManager implements Managed {
    private final CuratorFramework curatorFramework;

    @Inject
    public ZookeeperManager(CuratorFramework curatorFramework) {
        this.curatorFramework = curatorFramework;
    }

    @Override
    public void start() throws Exception {
        curatorFramework.start();
        curatorFramework.blockUntilConnected();
    }

    @Override
    public void stop() throws Exception {
        curatorFramework.close();
    }
}
