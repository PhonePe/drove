package com.phonepe.drove.common;

import com.phonepe.drove.common.discovery.Constants;
import com.phonepe.drove.common.model.ApplicationInstanceSpec;
import com.phonepe.drove.common.model.TaskInstanceSpec;
import com.phonepe.drove.common.retry.*;
import com.phonepe.drove.models.application.checks.HTTPCheckModeSpec;
import com.phonepe.drove.models.common.ClusterState;
import com.phonepe.drove.models.common.ClusterStateData;
import lombok.val;
import net.jodah.failsafe.Failsafe;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static com.phonepe.drove.common.CommonUtils.*;
import static com.phonepe.drove.models.common.HTTPVerb.GET;
import static org.junit.jupiter.api.Assertions.*;

/**
 *
 */
class CommonUtilsTest {

    @Test
    void testSubList() {
        assertTrue(sublist(null, 0, 2).isEmpty());
        assertTrue(sublist(List.of(), 0, 2).isEmpty());
        assertTrue(sublist(List.of(1, 2, 3, 4), 4, 2).isEmpty());
        {
            val r = sublist(IntStream.rangeClosed(1, 100).boxed().toList(), 3, 10);
            assertEquals(10, r.size());
            assertTrue(IntStream.rangeClosed(4, 13).boxed().collect(Collectors.toSet()).containsAll(r));
        }
        {
            val r = sublist(IntStream.rangeClosed(1, 5).boxed().toList(), 3, 10);
            assertEquals(2, r.size());
            assertTrue(Set.of(4, 5).containsAll(r));
        }
    }

    @Test
    void testPolicy() {
        {
            val p = policy(new IntervalRetrySpec(Duration.ofSeconds(1)), null);
            assertEquals(Duration.ofSeconds(1), p.getDelay());
        }
        {
            val p = policy(new MaxDurationRetrySpec(Duration.ofSeconds(1)), null);
            assertEquals(Duration.ofSeconds(1), p.getMaxDuration());
        }
        {
            val p = policy(new MaxRetriesRetrySpec(5), null);
            assertEquals(5, p.getMaxAttempts());
        }
        {
            val p = policy(new RetryOnAllExceptionsSpec(), null);
            val excep = new AtomicBoolean();
            try {
                Failsafe.with(List.of(p))
                        .onComplete(e -> {
                            excep.set(e.getFailure() instanceof IllegalStateException);
                        })
                        .run(() -> {
                            throw new IllegalStateException("Test fail");
                        });
                fail("Should have thrown exception");
            }
            catch (Exception e) {
                assertTrue(excep.get());
            }
        }
        {
            val p = policy(new CompositeRetrySpec(
                    List.of(new IntervalRetrySpec(Duration.ofSeconds(1)),
                            new MaxRetriesRetrySpec(5))), null);
            assertEquals(Duration.ofSeconds(1), p.getDelay());
            assertEquals(5, p.getMaxAttempts());
        }
        {
            val p = CommonUtils.<Integer>policy(new MaxRetriesRetrySpec(5), x -> x < 5);
            val ctr = new AtomicInteger();
            assertEquals(5, Failsafe.with(List.of(p)).get(ctr::incrementAndGet));
        }
    }

    @Test
    void testIsInMaintenanceWindow() {
        assertFalse(CommonUtils.isInMaintenanceWindow(null));
        val currTime = new Date();
        { //It is in maintenance window
            val data = new ClusterStateData(ClusterState.MAINTENANCE, currTime);
            assertTrue(CommonUtils.isInMaintenanceWindow(data));
        }
        { //It is in normal mode but in maintenance window buffer time
            val data = new ClusterStateData(ClusterState.NORMAL,
                                            new Date(currTime.getTime() - Constants.EXECUTOR_REFRESH_INTERVAL.toMillis()));
            assertTrue(CommonUtils.isInMaintenanceWindow(data));
        }
        { //It is in maintenance window and after buffer time
            val data = new ClusterStateData(ClusterState.NORMAL,
                                            new Date(currTime.getTime() - 2 * Constants.EXECUTOR_REFRESH_INTERVAL.toMillis() + 1));
            assertFalse(CommonUtils.isInMaintenanceWindow(data));
        }
    }

    @Test
    void testInstanceId() {
        assertEquals("test", instanceId(new ApplicationInstanceSpec(null,
                                                                    null,
                                                                    "test",
                                                                    null,
                                                                    null,
                                                                    null,
                                                                    null,
                                                                    null,
                                                                    null,
                                                                    null,
                                                                    null,
                                                                    null,
                                                                    null)));
        assertEquals("test", instanceId(new TaskInstanceSpec(null,
                                                             null,
                                                             "test",
                                                             null,
                                                             null,
                                                             null,
                                                             null,
                                                             null)));
    }

    @Test
    void testCreateHttpClient() {
        assertNotNull(createHttpClient());
    }

    @Test
    void testInternalCreateHttpClient() {
        assertNotNull(createInternalHttpClient(new HTTPCheckModeSpec(HTTPCheckModeSpec.Protocol.HTTP,
                                                                     "admin",
                                                                     "/",
                                                                     GET,
                                                                     Set.of(200),
                                                                     "",
                                                                     io.dropwizard.util.Duration.seconds(1)),
                                               Duration.ofSeconds(2)));
    }
}