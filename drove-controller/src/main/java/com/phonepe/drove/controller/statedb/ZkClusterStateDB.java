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
