package com.phonepe.drove.controller.event;

import com.phonepe.drove.common.CommonTestUtils;
import com.phonepe.drove.controller.config.ControllerOptions;
import com.phonepe.drove.controller.managed.LeadershipEnsurer;
import com.phonepe.drove.controller.utils.EventUtils;
import com.phonepe.drove.models.events.events.DroveClusterMaintenanceModeSetEvent;
import io.appform.signals.signals.ConsumingSyncSignal;
import lombok.val;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.stream.IntStream;

import static com.phonepe.drove.controller.config.ControllerOptions.*;
import static com.phonepe.drove.models.events.DroveEventType.MAINTENANCE_MODE_SET;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 *
 */
class InMemoryEventStoreTest {

    @Test
    void test() {
        val leadershipEnsurer = mock(LeadershipEnsurer.class);
        when(leadershipEnsurer.onLeadershipStateChanged()).thenReturn(new ConsumingSyncSignal<>());
        val es = new InMemoryEventStore(leadershipEnsurer, ControllerOptions.DEFAULT);
        es.recordEvent(new DroveClusterMaintenanceModeSetEvent(EventUtils.controllerMetadata()));

        val res = es.latest(0, 10);

        assertNotNull(res);
        assertFalse(res.getEvents().isEmpty());
        assertEquals(1, res.getEvents().size());
        val summary = es.summarize(0);
        assertNotNull(es);
        assertEquals(1, summary.getEventsCount().getOrDefault(MAINTENANCE_MODE_SET, -1L));
    }

    @Test
    void testEventStoreSizeLimit() {
        val leadershipEnsurer = mock(LeadershipEnsurer.class);
        when(leadershipEnsurer.onLeadershipStateChanged()).thenReturn(new ConsumingSyncSignal<>());
        val es = new InMemoryEventStore(leadershipEnsurer, new ControllerOptions(DEFAULT_STALE_CHECK_INTERVAL,
                                                                                 DEFAULT_STALE_APP_AGE,
                                                                                 DEFAULT_MAX_STALE_INSTANCES_COUNT,
                                                                                 DEFAULT_STALE_INSTANCE_AGE,
                                                                                 DEFAULT_STALE_TASK_AGE,
                                                                                 DEFAULT_MAX_EVENTS_STORAGE_SIZE,
                                                                                 ClusterOpSpec.DEFAULT_CLUSTER_OP_TIMEOUT,
                                                                                 ClusterOpSpec.DEFAULT_CLUSTER_OP_PARALLELISM,
                                                                                 DEFAULT_JOB_RETRY_COUNT,
                                                                                 DEFAULT_JOB_RETRY_INTERVAL,
                                                                                 DEFAULT_INSTANCE_STATE_CHECK_RETRY_INTERVAL,
                                                                                 DEFAULT_AUDITED_METHODS,
                                                                                 false,
                                                                                 false),
                                        Duration.ofSeconds(1));
        IntStream.rangeClosed(1, 200)
                .forEach(i -> {
                    CommonTestUtils.delay(Duration.ofMillis(1)); //Otherwise all time will be same
                    es.recordEvent(new DroveClusterMaintenanceModeSetEvent(EventUtils.controllerMetadata()));
                });
        {
            val res = es.latest(0, Integer.MAX_VALUE);
            assertNotNull(res);
            assertEquals(200, res.getEvents().size());
            val summary = es.summarize(0);
            assertNotNull(summary);
            assertFalse(summary.getEventsCount().isEmpty());
            assertEquals(200, summary.getEventsCount().values().stream().reduce(0L, Long::sum));
        }
        //Wait for cleanup to hit
        CommonTestUtils.delay(Duration.ofSeconds(3));
        {
            val res = es.latest(0, Integer.MAX_VALUE);
            assertNotNull(res);
            assertTrue(res.getEvents().isEmpty());
            val summary = es.summarize(0);
            assertNotNull(summary);
            assertTrue(summary.getEventsCount().isEmpty());
        }
    }

    @Test
    void testLeadershipChange() {
        val leadershipEnsurer = mock(LeadershipEnsurer.class);
        val leaderChanged = new ConsumingSyncSignal<Boolean>();
        when(leadershipEnsurer.onLeadershipStateChanged()).thenReturn(leaderChanged);
        val es = new InMemoryEventStore(leadershipEnsurer, ControllerOptions.DEFAULT);
        es.recordEvent(new DroveClusterMaintenanceModeSetEvent(EventUtils.controllerMetadata()));

        {
            val res = es.latest(0, 10);
            assertNotNull(res);
            assertFalse(res.getEvents().isEmpty());
            assertEquals(1, res.getEvents().size());
        }
        leaderChanged.dispatch(false);
        {
            val res = es.latest(0, 10);
            assertNotNull(res);
            assertTrue(res.getEvents().isEmpty());
        }
    }
}