package com.phonepe.drove.controller.managed;

import com.phonepe.drove.controller.statedb.ClusterStateDB;
import com.phonepe.drove.models.common.ClusterState;
import io.dropwizard.lifecycle.Managed;
import lombok.extern.slf4j.Slf4j;
import ru.vyarus.dropwizard.guice.module.installer.order.Order;

import javax.inject.Inject;

/**
 *
 */
@Slf4j
@Order(5)
public class ClusterStateManager implements Managed {
    private final ClusterStateDB clusterStateDB;

    @Inject
    public ClusterStateManager(ClusterStateDB clusterStateDB) {
        this.clusterStateDB = clusterStateDB;
    }


    @Override
    public void start() throws Exception {
        clusterStateDB.currentState()
                .ifPresentOrElse(clusterStateData -> log.info("Cluster is in mode: {}", clusterStateData.getState()),
                                 () -> {
                                     if (clusterStateDB.setClusterState(ClusterState.NORMAL).isPresent()) {
                                         log.info("Cluster state initialized");
                                     }
                                     else {
                                         log.warn("Cluster state could not be initialized");
                                     }
                                 });
    }

    @Override
    public void stop() throws Exception {
        //Nothing to do here
    }
}
