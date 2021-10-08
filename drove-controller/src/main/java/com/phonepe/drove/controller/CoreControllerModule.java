package com.phonepe.drove.controller;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.phonepe.drove.common.CommonUtils;
import com.phonepe.drove.common.discovery.NodeDataStore;
import com.phonepe.drove.common.discovery.ZkNodeDataStore;
import com.phonepe.drove.common.zookeeper.ZkConfig;
import com.phonepe.drove.controller.resources.ClusterResourcesDB;
import com.phonepe.drove.controller.resources.MapBasedClusterResourcesDB;
import com.phonepe.drove.controller.statedb.ApplicationStateDB;
import com.phonepe.drove.controller.statedb.ExecutorStateDB;
import com.phonepe.drove.controller.statedb.MapBasedApplicationStateDB;
import com.phonepe.drove.controller.statedb.MapBasedExecutorStateDB;
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
        bind(ExecutorStateDB.class).to(MapBasedExecutorStateDB.class);
        bind(ClusterResourcesDB.class).to(MapBasedClusterResourcesDB.class);
        bind(ApplicationStateDB.class).to(MapBasedApplicationStateDB.class);
    }
}
