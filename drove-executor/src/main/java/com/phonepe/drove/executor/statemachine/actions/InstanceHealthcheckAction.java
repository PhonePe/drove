package com.phonepe.drove.executor.statemachine.actions;

import com.phonepe.drove.common.StateData;
import com.phonepe.drove.executor.Utils;
import com.phonepe.drove.executor.checker.Checker;
import com.phonepe.drove.executor.statemachine.InstanceAction;
import com.phonepe.drove.executor.statemachine.InstanceActionContext;
import com.phonepe.drove.models.application.CheckResult;
import com.phonepe.drove.models.instance.InstanceInfo;
import com.phonepe.drove.models.instance.InstanceState;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 *
 */
@Slf4j
public class InstanceHealthcheckAction extends InstanceAction {
    private final ScheduledExecutorService executorService = Executors.newSingleThreadScheduledExecutor();

    private final Lock checkLock = new ReentrantLock();
    private final Condition stateChanged = checkLock.newCondition();
    private final AtomicReference<CheckResult> currentResult = new AtomicReference<>(null);
    private final AtomicBoolean stop = new AtomicBoolean();
    private ScheduledFuture<?> checkerJob;

    @Override
    protected StateData<InstanceState, InstanceInfo> executeImpl(
            InstanceActionContext context, StateData<InstanceState, InstanceInfo> currentState) {
        val healthcheckSpec = context.getInstanceSpec().getHealthcheck();
        val checker = Utils.createChecker(context, currentState.getData(), healthcheckSpec);
        log.info("Starting healthcheck");
        try {
            checkerJob = executorService.scheduleWithFixedDelay(new HealthChecker(checker,
                                                                                  checkLock,
                                                                                  stateChanged,
                                                                                  currentResult),
                                                                healthcheckSpec.getInitialDelay().toMilliseconds(),
                                                                healthcheckSpec.getInterval().toMilliseconds(),
                                                                TimeUnit.MILLISECONDS);
            checkLock.lock();
            monitor();
            if (stop.get()) {
                log.info("Stopping health-checks");
                return StateData.create(InstanceState.STOPPING, currentState.getData());
            }
            val result = currentResult.get();
            if (null == result || result.getStatus().equals(CheckResult.Status.UNHEALTHY)) {
                return StateData.errorFrom(
                        currentState,
                        InstanceState.UNHEALTHY,
                        "Healthcheck failed" + (null != result
                                                ? ":" + result.getMessage()
                                                : ""));
            }
        }
        catch (Exception e) {
            log.info("Error occurred: ", e);
        }
        finally {
            stopJob();
            checkLock.unlock();
        }
        return StateData.errorFrom(currentState, InstanceState.UNHEALTHY, "Node is unhealthy");
    }

    private void monitor() {
        while (!stop.get() && isHealthy()) {
            try {
                stateChanged.await();
            }
            catch (InterruptedException e) {
                log.info("Health check monitor thread interrupted.");
                Thread.currentThread().interrupt();
            }
        }
    }

    private void stopJob() {
        checkerJob.cancel(true);
        executorService.shutdownNow();
        try {
            val stopped = executorService.awaitTermination(5, TimeUnit.SECONDS);
            if (!stopped) {
                log.warn("Health check executor has not been shut down properly.");
            }
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public void stop() {
        checkLock.lock();
        try {
            stop.set(true);
            stateChanged.signalAll();
            log.debug("Stop called");
        }
        finally {
            checkLock.unlock();
        }
    }

    private boolean isHealthy() {
        return currentResult.get() == null || currentResult.get().getStatus().equals(CheckResult.Status.HEALTHY);
    }

    private static class HealthChecker implements Runnable {
        private final Checker checker;
        private final Lock lock;
        private final Condition condition;
        private final AtomicReference<CheckResult> currentResult;

        private HealthChecker(
                Checker checker,
                Lock lock,
                Condition condition,
                AtomicReference<CheckResult> currentResult) {
            this.checker = checker;
            this.lock = lock;
            this.condition = condition;
            this.currentResult = currentResult;
        }

        @Override
        public void run() {
            lock.lock();
            try {
                log.debug("Starting healthcheck call");
                val result = checker.call();
                log.info("Health check results: {}", result);
                currentResult.set(result);
            }
            catch (Exception e) {
                currentResult.set(new CheckResult(CheckResult.Status.UNHEALTHY, "Error running healthcheck: " + e));
            }
            finally {
                condition.signalAll();
                lock.unlock();
            }
        }
    }
}
