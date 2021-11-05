package com.phonepe.drove.common;

import lombok.Data;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 *
 */
@Data
public class ActionContext<D> {
    private final AtomicBoolean alreadyStopped = new AtomicBoolean();

    private final AtomicReference<D> currentUpdate = new AtomicReference<>();

    public D getUpdate() {
        return currentUpdate.get();
    }

    public boolean recordUpdate(D update) {
        return null == currentUpdate.compareAndExchange(null, update);
    }

    public boolean resetUpdate() {
        return null != currentUpdate.getAndUpdate(oldValue -> null);
    }
}
