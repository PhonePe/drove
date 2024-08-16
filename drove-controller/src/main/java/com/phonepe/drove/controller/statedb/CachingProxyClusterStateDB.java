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

package com.phonepe.drove.controller.statedb;

import com.phonepe.drove.controller.managed.LeadershipEnsurer;
import com.phonepe.drove.models.common.ClusterState;
import com.phonepe.drove.models.common.ClusterStateData;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.StampedLock;

/**
 *
 */
@Singleton
@Slf4j
public class CachingProxyClusterStateDB implements ClusterStateDB {
    private final ClusterStateDB root;

    private final AtomicReference<ClusterStateData> stateData = new AtomicReference<>();
    private final StampedLock lock = new StampedLock();

    @Inject
    public CachingProxyClusterStateDB(
            @Named("StoredClusterStateDB") ClusterStateDB root,
            final LeadershipEnsurer leadershipEnsurer) {
        this.root = root;
        leadershipEnsurer.onLeadershipStateChanged().connect(this::purge);
    }

    @Override
    public Optional<ClusterStateData> setClusterState(ClusterState state) {
        val stamp = lock.writeLock();
        try {
            val updatedState = root.setClusterState(state);
            updatedState.ifPresent(newValue -> {
                log.info("Cluster state updated to: {}", newValue.getState());
                stateData.set(newValue);
            });
            return updatedState;
        }
        finally {
            lock.unlockWrite(stamp);
        }
    }

    @Override
    public Optional<ClusterStateData> currentState() {
        var stamp = lock.readLock();
        try {
            if (stateData.get() == null) {
                val status = lock.tryConvertToWriteLock(stamp);
                if (status == 0) { //Did not loc, try explicit lock
                    lock.unlockRead(stamp);
                    stamp = lock.writeLock();
                }
                else {
                    stamp = status;
                }
                root.currentState().ifPresent(stateData::set);
            }
            return Optional.ofNullable(stateData.get());
        }
        finally {
            lock.unlock(stamp);
        }
    }

    private void purge(boolean leader) {
        val stamp = lock.writeLock();
        try {
            stateData.set(null);
        }
        finally {
            lock.unlock(stamp);
        }
    }
}
