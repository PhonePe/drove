package com.phonepe.drove.common;

import lombok.Data;

import java.util.Optional;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 *
 */
@Data
public class ActionContext<D> {
    private final AtomicBoolean alreadyStopped = new AtomicBoolean();

    private final LinkedBlockingQueue<D> currentUpdate = new LinkedBlockingQueue<>();

    public Optional<D> getUpdate() {
        return Optional.ofNullable(currentUpdate.peek());
    }

    public boolean recordUpdate(D update) {
        return currentUpdate.offer(update);
    }

    public boolean ackUpdate() {
        return currentUpdate.poll() != null;
    }
}
