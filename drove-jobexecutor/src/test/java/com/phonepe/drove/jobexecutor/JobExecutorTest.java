/*
 *  Copyright (c) 2022 Original Author(s), PhonePe India Pvt. Ltd.
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

package com.phonepe.drove.jobexecutor;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.IntStream;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

/**
 *
 */
@Slf4j
class JobExecutorTest {
    private final JobExecutor<Integer> exec = new JobExecutor<>(Executors.newSingleThreadExecutor());
    private final ThreadFactory threadFactory = Executors.defaultThreadFactory();
    @Test
    void test() {
        val responseCombiner = new IntResponseCombiner();
        val done = new AtomicBoolean();
        val failed = new AtomicBoolean();
        exec.onComplete().connect(res -> validateResults(done, failed, res));
        val topology = JobTopology.<Integer>builder().addJob(IntStream.rangeClosed(1, 10)
                                                                     .mapToObj(i -> (Job<Integer>)new Adder(i))
                                                                     .toList())
                .build();
        exec.schedule(topology, responseCombiner, r -> {});
        waitUntil(done::get);
        assertTrue(done.get());
        assertFalse(failed.get());
    }

    @Test
    void testEmpty() {
        val responseCombiner = new IntResponseCombiner();
        val done = new AtomicBoolean();
        exec.onComplete().connect(res -> done.set(true));
        val topology = JobTopology.<Integer>builder()
                .addParallel(32, List.of())
                .build();
        exec.schedule(topology, responseCombiner, r -> {});
        waitUntil(done::get);
        assertTrue(done.get());
    }

    @Test
    void testParallel() {
        val responseCombiner = new IntResponseCombiner();
        val done = new AtomicBoolean();
        val failed = new AtomicBoolean();
        exec.onComplete().connect(res -> validateResults(done, failed, res));

        exec.schedule(Collections.singletonList(
                new JobLevel<>(3,
                               threadFactory,
                               IntStream.rangeClosed(1, 10)
                                       .mapToObj(i -> (Job<Integer>)new Adder(i))
                                       .toList())), responseCombiner, r -> {});
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
                .withThreadFactory(threadFactory)
                .addJob(new Adder(1))
                .addParallel(3, IntStream.rangeClosed(1, 10)
                        .mapToObj(i -> (Job<Integer>)new Adder(i))
                        .toList())
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
                        .mapToObj(i -> (Job<Integer>)new Adder(i))
                        .toList())
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

    public void waitUntil(final Callable<Boolean> condition) {
        waitUntil(condition, Duration.ofMinutes(3));
    }

    public void waitUntil(final Callable<Boolean> condition, final Duration duration) {
        await()
                .pollDelay(Duration.ofSeconds(1))
                .timeout(duration)
                .until(condition);
    }

    public void delay(final Duration duration) {
        val wait = duration.toMillis();
        val end = new Date(new Date().getTime() + wait);
        await()
                .pollDelay(Duration.ofSeconds(1))
                .timeout(wait + 5_000, TimeUnit.SECONDS)
                .until(() -> new Date().after(end));
    }
}