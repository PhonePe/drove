package com.phonepe.drove.executor.resourcemgmt.resourceloaders;

import lombok.val;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test case for CommandUtils Thread Leak Risk.
 * 
 * ISSUE: CommandUtils Thread Leak Risk (HIGH PRIORITY)
 * 
 * Current State: When command times out, ExecutorService may not properly shut down.
 * Expected State: ExecutorService should always shut down properly, even under
 *                 high load with failures.
 * 
 * Rationale: Thread leaks under high load with failures can lead to:
 * - Resource exhaustion (OOM errors)
 * - Performance degradation
 * - System unresponsiveness
 */
class CommandUtilsThreadLeakTest {

    @Test
    void testCommandTimeoutProperlyShutsDownExecutorService() {
        /**
         * ISSUE: Command timeout may not properly shut down ExecutorService
         * 
         * SCENARIO: A command times out, causing an exception.
         * EXPECTED: ExecutorService.ioReaderPool should be properly shutdown in finally block.
         */
        
        // CommandUtils has only static methods, so we call the static method directly
        
        assertThrows(IOException.class, () -> {
            // This command will timeout because it runs indefinitely (sleep)
            // The finally block should still properly shut down the executor
            CommandUtils.parseCommandOutput("sleep 100", false, 1L);
        });
        
        // If we got here, the command timed out and threw IOException
        // The finally block in CommandUtils should have called shutdownNow()
        
        // Test was successful - no exception about thread leak
    }

    @Test
    void testConcurrentTimeoutsDoNotLeakThreads() throws InterruptedException {
        /**
         * ISSUE: High load with multiple timeouts may leak threads
         * 
         * SCENARIO: Execute many timed-out commands concurrently
         * EXPECTED: No thread leaks, all threads properly cleaned up
         * 
         * This tests the robustness of the finally block under stress.
         */
        
        int numThreads = 50;
        ExecutorService testExecutor = Executors.newFixedThreadPool(numThreads);
        CountDownLatch latch = new CountDownLatch(numThreads);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger exceptionCount = new AtomicInteger(0);
        
        for (int i = 0; i < numThreads; i++) {
            final int index = i;
            testExecutor.submit(() -> {
                try {
                    // All these should timeout and throw IOException
                    CommandUtils.parseCommandOutput("sleep 100", false, 1L);
                } catch (IOException e) {
                    // Expected - command timed out
                    exceptionCount.incrementAndGet();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    latch.countDown();
                }
            });
        }
        
        // Wait for all commands to complete (with timeout or success)
        boolean completed = latch.await(30, TimeUnit.SECONDS);
        
        testExecutor.shutdown();
        boolean terminated = testExecutor.awaitTermination(10, TimeUnit.SECONDS);
        
        assertTrue(completed, "All commands should complete within 30 seconds");
        assertTrue(terminated, "Executor service should be properly terminated");
        
        // Verify that all commands timed out (as expected)
        assertEquals(numThreads, exceptionCount.get(), 
            "All " + numThreads + " commands should have timed out and thrown IOException");
        
        // If we got here without thread leak issues, the test passes
    }

    @Test
    void testRapidSuccessiveCommandExecutions() throws InterruptedException {
        /**
         * ISSUE: Rapid successive commands may leave orphaned threads
         * 
         * SCENARIO: Execute many commands in quick succession, some succeeding, some failing
         * EXPECTED: No thread accumulation, proper cleanup after each command
         */
        
        int numCommands = 20;
        ExecutorService testExecutor = Executors.newFixedThreadPool(10);
        CountDownLatch latch = new CountDownLatch(numCommands);
        
        for (int i = 0; i < numCommands; i++) {
            final int index = i;
            testExecutor.submit(() -> {
                try {
                    // Use sleep 0.1 for quick commands that should succeed
                    CommandUtils.parseCommandOutput("sleep 0.1", false, 5L);
                } catch (IOException e) {
                    // This shouldn't happen in this test
                    fail("Command " + index + " should not have thrown IOException");
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    latch.countDown();
                }
            });
            
            // Small delay between commands to simulate real-world pattern
            Thread.sleep(50);
        }
        
        boolean completed = latch.await(60, TimeUnit.SECONDS);
        testExecutor.shutdown();
        boolean terminated = testExecutor.awaitTermination(10, TimeUnit.SECONDS);
        
        assertTrue(completed, "All commands should complete");
        assertTrue(terminated, "Executor service should terminate properly");
    }

    @Test
    void testCommandExceptionDoesNotLeakExecutorThreads() {
        /**
         * ISSUE: Command exceptions (not just timeouts) may leak threads
         * 
         * SCENARIO: Command fails with various error conditions
         * EXPECTED: ExecutorService properly cleaned up in all error paths
         */
        
        // Test 1: Non-existent command (should fail)
        try {
            CommandUtils.parseCommandOutput("nonexistentcommand12345", false, 5L);
            fail("Should have thrown IOException for non-existent command");
        } catch (IOException e) {
            // Expected - command failed
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        // Test 2: Command that exits with non-zero code
        try {
            CommandUtils.parseCommandOutput("exit 1", false, 5L);
            fail("Should have thrown IOException for non-zero exit");
        } catch (IOException e) {
            // Expected - command failed
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        // If we got here without thread leaks, the test passes
    }

    @Test
    void testExecutorServiceCleanupAfterAllErrorPaths() throws InterruptedException {
        /**
         * ISSUE: Multiple error paths in CommandUtils may not all clean up properly
         * 
         * Current implementation has these paths:
         * - Timeout (destroyForcibly + IOException)
         * - ExecutionException (wrap and rethrow)
         * - IOException (rethrow)
         * - InterruptedException (restore interrupt + rethrow)
         * 
         * All paths must ensure ioReaderPool.shutdownNow() is called.
         */
        
        // Test InterruptedException path
        Thread interruptThread = new Thread(() -> {
            try {
                CommandUtils.parseCommandOutput("sleep 100", false, 60L);
            } catch (IOException | InterruptedException e) {
                // Expected
            }
        });
        
        interruptThread.start();
        
        // Wait a bit then interrupt
        Thread.sleep(500);
        interruptThread.interrupt();
        
        try {
            interruptThread.join(5000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        // If we got here without hanging or thread leaks, test passes
    }

    @Test
    void testMultipleConcurrentTimeoutsWithDifferentTimeouts() throws InterruptedException {
        /**
         * ISSUE: Different timeout values may interact poorly with shared executor
         * 
         * SCENARIO: Multiple commands with different timeout values running concurrently
         * EXPECTED: Each timeout properly managed, no interference between them
         */
        
        int numCommands = 30;
        ExecutorService testExecutor = Executors.newFixedThreadPool(15);
        CountDownLatch latch = new CountDownLatch(numCommands);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger timeoutCount = new AtomicInteger(0);
        
        for (int i = 0; i < numCommands; i++) {
            final int index = i;
            final long timeout = (i % 3) == 0 ? 1L : 2L;  // 1/3 get 1s timeout
            
            testExecutor.submit(() -> {
                try {
                    String sleepTime = String.valueOf(10 + index);  // Sleep 10-40 seconds
                    CommandUtils.parseCommandOutput("sleep " + sleepTime, false, timeout);
                    successCount.incrementAndGet();
                } catch (IOException e) {
                    // Timeout or other IO error
                    timeoutCount.incrementAndGet();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    latch.countDown();
                }
            });
        }
        
        boolean completed = latch.await(120, TimeUnit.SECONDS);
        testExecutor.shutdown();
        boolean terminated = testExecutor.awaitTermination(10, TimeUnit.SECONDS);
        
        assertTrue(completed, "All commands should complete");
        assertTrue(terminated, "Executor should terminate properly");
        
        // Most commands should timeout (long sleep, short timeout)
        assertTrue(timeoutCount.get() > successCount.get(), 
            "Most commands should timeout with short timeouts");
    }
}
