/*
 *  Copyright (c) 2022 Original Author(s), PhonePe India Pvt. Ltd.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

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
