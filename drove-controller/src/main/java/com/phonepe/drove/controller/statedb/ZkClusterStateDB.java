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

package com.phonepe.drove.controller.statedb;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.phonepe.drove.common.zookeeper.ZkUtils;
import com.phonepe.drove.models.common.ClusterState;
import com.phonepe.drove.models.common.ClusterStateData;
import org.apache.curator.framework.CuratorFramework;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Date;
import java.util.Optional;

/**
 *
 */
@Singleton
public class ZkClusterStateDB implements ClusterStateDB {

    @SuppressWarnings("java:S1075")
    private static final String PATH = "/cluster/maintenance";

    private final CuratorFramework curatorFramework;
    private final ObjectMapper mapper;

    @Inject
    public ZkClusterStateDB(CuratorFramework curatorFramework, ObjectMapper mapper) {
        this.curatorFramework = curatorFramework;
        this.mapper = mapper;
    }

    @Override
    public Optional<ClusterStateData> setClusterState(ClusterState state) {
        if(ZkUtils.setNodeData(curatorFramework, PATH, mapper, new ClusterStateData(state, new Date()))) {
            return currentState();
        }
        return Optional.empty();
    }

    @Override
    public Optional<ClusterStateData> currentState() {
        return Optional.ofNullable(ZkUtils.readNodeData(curatorFramework, PATH, mapper, ClusterStateData.class));
    }
}
