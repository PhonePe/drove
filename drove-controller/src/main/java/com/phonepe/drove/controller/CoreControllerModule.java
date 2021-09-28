package com.phonepe.drove.controller;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.phonepe.drove.common.CommonUtils;
import com.phonepe.drove.common.discovery.NodeDataStore;
import com.phonepe.drove.common.discovery.ZkNodeDataStore;
import com.phonepe.drove.common.zookeeper.ZkConfig;
import org.apache.curator.framework.CuratorFramework;

import javax.inject.Singleton;

/**
 *
 */
public class CoreControllerModule extends AbstractModule {
    @Provides
    @Singleton
    public ZkConfig zkConfig(final AppConfig appConfig) {
        return appConfig.getZookeeper();
    }

    @Provides
    @Singleton
    public CuratorFramework curator(ZkConfig config) {
        return CommonUtils.buildCurator(config);
    }

    @Override
    protected void configure() {
        bind(NodeDataStore.class).to(ZkNodeDataStore.class);
    }
}
