package com.phonepe.drove.controller.jobexecutor;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Collections;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static com.phonepe.drove.common.CommonTestUtils.delay;
import static com.phonepe.drove.common.CommonTestUtils.waitUntil;
import static org.junit.jupiter.api.Assertions.*;

/**
 *
 */
@Slf4j
class JobExecutorTest {
    private final JobExecutor<Integer> exec = new JobExecutor<>(Executors.newSingleThreadExecutor());

    @Test
    void test() {
        val responseCombiner = new IntResponseCombiner();
        val done = new AtomicBoolean();
        val failed = new AtomicBoolean();
        exec.onComplete().connect(res -> validateResults(done, failed, res));
        val topology = JobTopology.<Integer>builder().addJob(IntStream.rangeClosed(1, 10)
                                                                     .mapToObj(Adder::new)
                                                                     .collect(Collectors.toUnmodifiableList()))
                .build();
        exec.schedule(topology, responseCombiner, r -> {});
        waitUntil(done::get);
        assertTrue(done.get());
        assertFalse(failed.get());
    }

    @Test
    void testParallel() {
        val responseCombiner = new IntResponseCombiner();
        val done = new AtomicBoolean();
        val failed = new AtomicBoolean();
        exec.onComplete().connect(res -> validateResults(done, failed, res));

        exec.schedule(Collections.singletonList(
                new JobLevel<>(3,
                               IntStream.rangeClosed(1, 10)
                                       .mapToObj(Adder::new)
                                       .collect(Collectors.toUnmodifiableList()))), responseCombiner, r -> {});
        log.debug("Waiting for jobs to complete");
        waitUntil(done::get);
        assertTrue(done.get());
        assertFalse(failed.get());
    }

    @Test
    void testMixed() {
        val responseCombiner = new IntResponseCombiner();
        val done = new AtomicBoolean();
        val failed = new AtomicBoolean();
        exec.onComplete().connect(res -> validateResults(done, failed, res, 57));
        val topology = JobTopology.<Integer>builder()
                .addJob(new Adder(1))
                .addParallel(3, IntStream.rangeClosed(1, 10)
                        .mapToObj(Adder::new)
                        .collect(Collectors.toUnmodifiableList()))
                .addJob(new Adder(1))
                .build();
        exec.schedule(topology, responseCombiner, r -> {});
        log.debug("Waiting");
        waitUntil(done::get);
        assertTrue(done.get());
        assertFalse(failed.get());
    }

    @Test
    void testFailure() {
        val done = new AtomicBoolean();
        val failed = new AtomicBoolean();
        exec.onComplete().connect(res -> {
            log.info("Jobs completed");
            try {
                assertEquals(56, res.getResult());
                assertEquals(IllegalStateException.class, res.getFailure().getClass());
//                assertFalse(res.isCancelled());
            }
            catch (Throwable t) {
                log.error("Test failed: ", t);
                failed.set(true);
            }
            finally {
                done.set(true);
                log.debug("Done");
            }
        });
        val topology = JobTopology.<Integer>builder()
                .addJob(new Adder(1))
                .addParallel(3, IntStream.rangeClosed(1, 10)
                        .mapToObj(Adder::new)
                        .collect(Collectors.toUnmodifiableList()))
                .addJob(new ErrorJob())
                .addJob(new Adder(1))
                .build();
        exec.schedule(topology, new IntResponseCombiner(), r -> {});
        log.debug("Waiting");
        waitUntil(done::get);
        assertTrue(done.get());
        assertFalse(failed.get());
    }

    @SneakyThrows
    @Test
    void testCancellation() {
        val done = new AtomicBoolean();
        val failed = new AtomicBoolean();
        exec.onComplete().connect(res -> {
            log.info("Jobs completed");
            try {
                assertEquals(1, res.getResult());
                assertNull(res.getFailure());
//                assertFalse(res.isCancelled());
            }
            catch (Throwable t) {
                log.error("Test failed: ", t);
                failed.set(true);
            }
            finally {
                done.set(true);
                log.debug("Done");
            }
        });
        val topology = JobTopology.<Integer>builder()
                .addJob(new Adder(1))
                .addJob(new StuckJob())
                .addJob(new Adder(1))
                .build();
        val execId = exec.schedule(Collections.singletonList(topology), new IntResponseCombiner(), r -> {});
        log.debug("Waiting");
        delay(Duration.ofSeconds(1));
        exec.cancel(execId);
        waitUntil(done::get);
        assertTrue(done.get());
        assertFalse(failed.get());
    }

    private void validateResults(AtomicBoolean done, AtomicBoolean failed, JobExecutionResult<Integer> res) {
        validateResults(done, failed, res, 55);
    }

    private void validateResults(
            AtomicBoolean done,
            AtomicBoolean failed,
            JobExecutionResult<Integer> res,
            int expectation) {
        log.info("Jobs completed");
        try {
            assertEquals(expectation, res.getResult());
            assertNull(res.getFailure());
//            assertFalse(res.isCancelled());
        }
        catch (Throwable t) {
            log.error("Test failed: ", t);
            failed.set(true);
        }
        finally {
            done.set(true);
            log.debug("Done");
        }
    }

}