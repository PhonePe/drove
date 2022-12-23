package com.phonepe.drove.controller.event;

import com.phonepe.drove.controller.config.ControllerOptions;
import com.phonepe.drove.controller.managed.LeadershipEnsurer;
import lombok.val;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.StampedLock;

/**
 *
 */
@Singleton
@SuppressWarnings("rawtypes")
public class InMemoryEventStore implements EventStore {
    private final Map<Long, DroveEvent> events;
    private final StampedLock lock = new StampedLock();

    @Inject
    public InMemoryEventStore(LeadershipEnsurer leadershipEnsurer, ControllerOptions options) {
        leadershipEnsurer.onLeadershipStateChanged().connect(this::nuke);
        val maxEventCount = options.getMaxEventsStorageCount() > 0
                            ? options.getMaxEventsStorageCount()
                            : ControllerOptions.DEFAULT_MAX_STALE_INSTANCES_COUNT;
        events = new LinkedHashMap<>(maxEventCount) {

            @Override
            protected boolean removeEldestEntry(Map.Entry<Long, DroveEvent> eldest) {
                return size() > maxEventCount;
            }
        };
    }

    @Override
    public void recordEvent(DroveEvent event) {
        val stamp = lock.writeLock();
        try {
            events.put(event.getTime().getTime(), event);
        }
        finally {
            lock.unlock(stamp);
        }
    }

    @Override
    public List<DroveEvent> latest(long lastSyncTime, int size) {
        val stamp = lock.readLock();
        try {
            return events.entrySet()
                    .stream()
                    .filter(e -> e.getKey() > lastSyncTime)
                    .sorted(Map.Entry.<Long, DroveEvent>comparingByKey().reversed())
                    .map(Map.Entry::getValue)
                    .limit(size)
                    .toList();
        }
        finally {
            lock.unlock(stamp);
        }
    }

    private void nuke(boolean leader) {
        val stamp = lock.writeLock();
        try {
            events.clear();
        }
        finally {
            lock.unlock(stamp);
        }
    }
}
