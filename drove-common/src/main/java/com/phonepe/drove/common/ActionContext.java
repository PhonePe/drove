package com.phonepe.drove.common;

import lombok.Data;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 *
 */
@Data
public class ActionContext<D> {
    private final AtomicBoolean alreadyStopped = new AtomicBoolean();

    private final AtomicReference<D> currentUpdate = new AtomicReference<>();

    public Optional<D> getUpdate() {
        return Optional.ofNullable(currentUpdate.get());
    }

    public boolean recordUpdate(D update) {
        return currentUpdate.compareAndSet(null, update);
    }

    public boolean ackUpdate() {
        return currentUpdate.getAndSet(null) != null;
    }
}
