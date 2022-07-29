package com.phonepe.drove.controller.managed;

import io.dropwizard.lifecycle.Managed;
import lombok.extern.slf4j.Slf4j;
import org.apache.curator.framework.CuratorFramework;
import ru.vyarus.dropwizard.guice.module.installer.order.Order;

import javax.inject.Inject;
import javax.inject.Singleton;

/**
 *
 */
@Slf4j
@Order(0)
@Singleton
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
        log.info("Zookeeper connection completed");
    }

    @Override
    public void stop() throws Exception {
        log.debug("Shutting down {}", this.getClass().getSimpleName());
        curatorFramework.close();
        log.info("Zookeeper connection closed");
        log.debug("Shut down {}", this.getClass().getSimpleName());
    }
}
