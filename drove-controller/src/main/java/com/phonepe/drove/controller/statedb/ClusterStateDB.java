package com.phonepe.drove.controller.statedb;

import com.phonepe.drove.models.common.ClusterState;
import com.phonepe.drove.models.common.ClusterStateData;

import java.util.Optional;

/**
 *
 */
public interface ClusterStateDB {

    Optional<ClusterStateData> setClusterState(final ClusterState state);

    Optional<ClusterStateData> currentState();
}
