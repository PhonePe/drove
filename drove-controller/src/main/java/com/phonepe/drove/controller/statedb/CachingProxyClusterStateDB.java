package com.phonepe.drove.controller.statedb;

import com.phonepe.drove.models.common.ClusterState;
import com.phonepe.drove.models.common.ClusterStateData;
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
public class CachingProxyClusterStateDB implements ClusterStateDB {
    private final ClusterStateDB root;

    private final AtomicReference<ClusterStateData> stateData = new AtomicReference<>();
    private final StampedLock lock = new StampedLock();

    @Inject
    public CachingProxyClusterStateDB(@Named("StoredClusterStateDB") ClusterStateDB root) {
        this.root = root;
    }

    @Override
    public Optional<ClusterStateData> setClusterState(ClusterState state) {
        val stamp = lock.writeLock();
        try {
            val updatedState = root.setClusterState(state);
            updatedState.ifPresent(stateData::set);
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
            if(stateData.get() == null) {
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
}
