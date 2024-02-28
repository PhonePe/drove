package com.phonepe.drove.controller.engine;

import com.phonepe.drove.common.CommonUtils;
import com.phonepe.drove.controller.config.ControllerOptions;
import com.phonepe.drove.models.operation.ClusterOpSpec;
import io.dropwizard.util.Duration;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import net.jodah.failsafe.Failsafe;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static com.phonepe.drove.controller.config.ControllerOptions.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 *
 */
@Slf4j
class ControllerRetrySpecFactoryTest {

    @Test
    @SneakyThrows
    void testJobRetrySpec() {
        val value = new AtomicBoolean(false);
        val options = new ControllerOptions(DEFAULT_STALE_CHECK_INTERVAL,
                                            DEFAULT_STALE_APP_AGE,
                                            110,
                                            DEFAULT_STALE_INSTANCE_AGE,
                                            DEFAULT_STALE_TASK_AGE,
                                            DEFAULT_MAX_EVENTS_STORAGE_SIZE,
                                            DEFAULT_MAX_EVENT_STORAGE_DURATION,
                                            ClusterOpSpec.DEFAULT_CLUSTER_OP_TIMEOUT,
                                            ClusterOpSpec.DEFAULT_CLUSTER_OP_PARALLELISM,
                                            2,
                                            Duration.milliseconds(100),
                                            DEFAULT_INSTANCE_STATE_CHECK_RETRY_INTERVAL,
                                            false,
                                            false);
        val retrySpec = new DefaultControllerRetrySpecFactory(options).jobRetrySpec();
        val policy = CommonUtils.<Boolean>policy(retrySpec, x -> !x);
        log.info("Start");
        val ctr = new AtomicInteger();
        Failsafe.with(policy)
                .onSuccess(e -> log.info("Success: {}", e))
                .onFailure(e -> log.info("Failure: {}", e))
                .onComplete(e -> log.info("Complete {}", e))
                .get(() -> {
                    log.info("Called");
                    ctr.incrementAndGet();
                    return value.get();
                });
        assertEquals(3, ctr.get()); //Num attempts = retry + 1
    }

}