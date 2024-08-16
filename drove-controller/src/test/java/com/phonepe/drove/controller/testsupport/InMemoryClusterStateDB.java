/*
 *  Copyright (c) 2024 Original Author(s), PhonePe India Pvt. Ltd.
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
