package com.phonepe.drove.eventslistener;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Stores offset in memory. If process restarts, this will get reset to 0.
 */
public class DroveEventPollingOffsetInMemoryStore implements DroveEventPollingOffsetStore {
    private final AtomicLong currentOffset = new AtomicLong();

    @Override
    public long getLastOffset() {
        return currentOffset.get();
    }

    @Override
    public void setLastOffset(long offset) {
        currentOffset.set(offset);
    }
}
