package com.phonepe.drove.eventslistener;

/**
 * Stores and returns last polling offset.
 */
public interface DroveEventPollingOffsetStore {
    long getLastOffset();
    void setLastOffset(long offset);
}
