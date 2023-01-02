package com.phonepe.drove.controller.event;

import com.phonepe.drove.common.CommonTestUtils;
import com.phonepe.drove.controller.config.ControllerOptions;
import com.phonepe.drove.controller.event.events.DroveClusterEvent;
import com.phonepe.drove.controller.managed.LeadershipEnsurer;
import com.phonepe.drove.controller.utils.EventUtils;
import io.appform.signals.signals.ConsumingSyncSignal;
import lombok.val;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.stream.IntStream;

import static org.junit.Assert.*;
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
        es.recordEvent(new DroveClusterEvent(DroveEventType.MAINTENANCE_MODE_SET,
                                             EventUtils.controllerMetadata()));

        val res = es.latest(0, 10);
        assertFalse(res.isEmpty());
        assertEquals(1, res.size());
    }

    @Test
    void testEventStoreSizeLimit() {
        val leadershipEnsurer = mock(LeadershipEnsurer.class);
        when(leadershipEnsurer.onLeadershipStateChanged()).thenReturn(new ConsumingSyncSignal<>());
        val es = new InMemoryEventStore(leadershipEnsurer, ControllerOptions.DEFAULT);
        IntStream.rangeClosed(1, 200)
                .forEach(i -> {
                    CommonTestUtils.delay(Duration.ofMillis(1)); //Otherwise all time will be same
                    es.recordEvent(new DroveClusterEvent(DroveEventType.MAINTENANCE_MODE_SET,
                                                         EventUtils.controllerMetadata()));
                });
        val res = es.latest(0, 200);
        assertEquals(100, res.size());
    }

    @Test
    void testLeadershipChange() {
        val leadershipEnsurer = mock(LeadershipEnsurer.class);
        val leaderChanged = new ConsumingSyncSignal<Boolean>();
        when(leadershipEnsurer.onLeadershipStateChanged()).thenReturn(leaderChanged);
        val es = new InMemoryEventStore(leadershipEnsurer, ControllerOptions.DEFAULT);
        es.recordEvent(new DroveClusterEvent(DroveEventType.MAINTENANCE_MODE_SET,
                                             EventUtils.controllerMetadata()));

        {
            val res = es.latest(0, 10);
            assertFalse(res.isEmpty());
            assertEquals(1, res.size());
        }
        leaderChanged.dispatch(false);
        {
            val res = es.latest(0, 10);
            assertTrue(res.isEmpty());
        }
    }
}