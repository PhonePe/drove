package com.phonepe.drove.controller.testsupport;

import com.phonepe.drove.controller.statedb.ClusterStateDB;
import com.phonepe.drove.models.common.ClusterState;
import com.phonepe.drove.models.common.ClusterStateData;

import java.util.Date;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

/**
 *
 */
public class InMemoryClusterStateDB implements ClusterStateDB {
    private final AtomicReference<ClusterStateData> state = new AtomicReference<>();

    @Override
    public Optional<ClusterStateData> setClusterState(ClusterState state) {
        this.state.set(new ClusterStateData(state, new Date()));
        return Optional.of(this.state.get());
    }

    @Override
    public Optional<ClusterStateData> currentState() {
        return Optional.ofNullable(this.state.get());
    }
}
