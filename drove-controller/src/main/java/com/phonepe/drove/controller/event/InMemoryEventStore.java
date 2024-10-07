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

package com.phonepe.drove.controller.event;

import com.google.common.annotations.VisibleForTesting;
import com.phonepe.drove.common.coverageutils.IgnoreInJacocoGeneratedReport;
import com.phonepe.drove.controller.config.ControllerOptions;
import com.phonepe.drove.controller.managed.LeadershipEnsurer;
import com.phonepe.drove.controller.metrics.ClusterMetricNames;
import com.phonepe.drove.controller.metrics.ClusterMetricsRegistry;
import com.phonepe.drove.models.api.DroveEventsList;
import com.phonepe.drove.models.api.DroveEventsSummary;
import com.phonepe.drove.models.events.DroveEvent;
import io.appform.signals.signals.ScheduledSignal;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.locks.StampedLock;
import java.util.stream.Collectors;

/**
 *
 */
@Singleton
@Slf4j
@SuppressWarnings("rawtypes")
public class InMemoryEventStore implements EventStore {
    private final TreeMap<Long, List<DroveEvent>> events;
    private final StampedLock lock = new StampedLock();
    private final ScheduledSignal cleaner;
    private final ClusterMetricsRegistry metricsRegistry;

    @Inject
    @IgnoreInJacocoGeneratedReport
    public InMemoryEventStore(LeadershipEnsurer leadershipEnsurer, ControllerOptions options,
                              ClusterMetricsRegistry metricsRegistry) {
        this(leadershipEnsurer, options, Duration.ofMinutes(1), metricsRegistry);
    }

    @VisibleForTesting
    InMemoryEventStore(LeadershipEnsurer leadershipEnsurer, ControllerOptions options, Duration checkDuration,
                       ClusterMetricsRegistry metricsRegistry) {
        this.metricsRegistry = metricsRegistry;
        leadershipEnsurer.onLeadershipStateChanged().connect(this::nuke);
        val maxEventStorageDurationMs = Objects.requireNonNullElse(options.getMaxEventsStorageDuration(),
                                                                   ControllerOptions.DEFAULT_MAX_EVENT_STORAGE_DURATION)
                .toMilliseconds();

        this.events = new TreeMap<>();
        this.cleaner = new ScheduledSignal(checkDuration);
        this.cleaner.connect(time -> {
            val stamp = lock.writeLock();
            try {
                val oldestAcceptableTime = System.currentTimeMillis() - maxEventStorageDurationMs;
                val olderEntries = events.headMap(oldestAcceptableTime);
                if (!olderEntries.isEmpty()) {
                    val size = olderEntries.size();
                    olderEntries.clear();
                    log.info("Cleaned {} event slots older than {}", size, new Date(oldestAcceptableTime));
                }
                else {
                    log.debug("No entries to clear");
                }
            }
            finally {
                lock.unlock(stamp);
            }
        });
    }

    @Override
    public void recordEvent(DroveEvent event) {
        val stamp = lock.writeLock();
        try {
            events.computeIfAbsent(event.getTime().getTime(), i -> new ArrayList<>()).add(event);
            metricsRegistry.markMeter(ClusterMetricNames.Meters.CLUSTER_EVENTS);
            metricsRegistry.markMeter(ClusterMetricNames.Meters.CLUSTER_EVENTS + "." + event.getType().name());
        }
        finally {
            lock.unlock(stamp);
        }
    }

    @Override
    public DroveEventsList latest(long lastSyncTime, int size) {
        val stamp = lock.readLock();
        try {
            return new DroveEventsList(events.tailMap(lastSyncTime)
                                               .entrySet()
                                               .stream()
                                               .filter(e -> e.getKey() > lastSyncTime)
                                               .sorted(Map.Entry.<Long, List<DroveEvent>>comparingByKey().reversed())
                                               .map(Map.Entry::getValue)
                                               .flatMap(Collection::stream)
                                               .limit(size)
                                               .toList(),
                                       events.isEmpty() ? lastSyncTime : events.lastKey());
        }
        finally {
            lock.unlock(stamp);
        }
    }

    @Override
    public DroveEventsSummary summarize(long lastSyncTime) {
        val stamp = lock.readLock();
        try {
            return new DroveEventsSummary(events.tailMap(lastSyncTime)
                                                  .entrySet()
                                                  .stream()
                                                  .filter(e -> e.getKey() > lastSyncTime)
                                                  .map(Map.Entry::getValue)
                                                  .flatMap(Collection::stream)
                                                  .collect(Collectors.groupingBy(DroveEvent::getType,
                                                                                 Collectors.counting())),
                                          events.isEmpty() ? lastSyncTime : events.lastKey());
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
