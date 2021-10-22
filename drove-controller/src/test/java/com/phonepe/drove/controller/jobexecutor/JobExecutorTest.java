package com.phonepe.drove.controller.jobexecutor;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 *
 */
@Slf4j
class JobExecutorTest {

    @Test
    void test() {
        val responseCombiner = new IntResponseCombiner();
        val exec = new JobExecutor<>(Executors.newSingleThreadExecutor(), responseCombiner);
        val done = new AtomicBoolean();
        val failed = new AtomicBoolean();
        exec.onComplete().connect(res -> validateResults(done, failed, res));

        exec.schedule(IntStream.rangeClosed(1, 10).mapToObj(Adder::new).collect(Collectors.toUnmodifiableList()));
        Awaitility.await()
                .timeout(300, TimeUnit.SECONDS)
                .until(done::get);
        assertTrue(done.get());
        assertFalse(failed.get());
    }

    @Test
    void testParallel() {
        val responseCombiner = new IntResponseCombiner();
        val exec = new JobExecutor<>(Executors.newSingleThreadExecutor(), responseCombiner);
        val done = new AtomicBoolean();
        val failed = new AtomicBoolean();
        exec.onComplete().connect(res -> validateResults(done, failed, res));

        exec.schedule(Collections.singletonList(
                new JobLevel<>(3,
                               IntStream.rangeClosed(1, 10)
                                       .mapToObj(Adder::new)
                                       .collect(Collectors.toUnmodifiableList()))));
        log.debug("Waiting for jobs to complete");
        Awaitility.await()
                .timeout(300, TimeUnit.SECONDS)
                .until(done::get);
        assertTrue(done.get());
        assertFalse(failed.get());
    }

    @Test
    void testMixed() {
        val responseCombiner = new IntResponseCombiner();
        val exec = new JobExecutor<>(Executors.newSingleThreadExecutor(), responseCombiner);
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
        exec.schedule(Collections.singletonList(topology));
        log.debug("Waiting");
        Awaitility.await()
                .timeout(3, TimeUnit.SECONDS)
                .until(done::get);
        assertTrue(done.get());
        assertFalse(failed.get());
    }

    @Test
    void testFailure() {
        val responseCombiner = new IntResponseCombiner();
        val exec = new JobExecutor<>(Executors.newSingleThreadExecutor(), responseCombiner);
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
        exec.schedule(Collections.singletonList(topology));
        log.debug("Waiting");
        Awaitility.await()
                .timeout(300, TimeUnit.SECONDS)
                .until(done::get);
        assertTrue(done.get());
        assertFalse(failed.get());
    }

    @SneakyThrows
    @Test
    void testCancellation() {
        val responseCombiner = new IntResponseCombiner();
        val exec = new JobExecutor<>(Executors.newSingleThreadExecutor(), responseCombiner);
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
        val execId = exec.schedule(Collections.singletonList(topology));
        log.debug("Waiting");
        Thread.sleep(1000);
        exec.cancel(execId);
        Awaitility.await()
                .timeout(300, TimeUnit.SECONDS)
                .until(done::get);
        assertTrue(done.get());
        assertFalse(failed.get());
    }

    private void validateResults(AtomicBoolean done, AtomicBoolean failed, JobExecutionResult<Integer> res) {
        validateResults(done, failed, res, 55);
    }

    private void validateResults(AtomicBoolean done, AtomicBoolean failed, JobExecutionResult<Integer> res, int expectation) {
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