package com.phonepe.drove.executor;

import com.phonepe.drove.common.StateData;
import com.phonepe.drove.executor.checker.Checker;
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
    public StateData<InstanceState, InstanceInfo> execute(
            InstanceActionContext context, StateData<InstanceState, InstanceInfo> currentState) {
        val healthcheckSpec = context.getInstanceSpec().getHealthcheck();
        val checker = Utils.createChecker(context, currentState.getData(), healthcheckSpec);
        log.info("Starting healthcheck");
        checkerJob = executorService.scheduleAtFixedRate(new HealthChecker(checker,
                                                                           checkLock,
                                                                           stateChanged,
                                                                           currentResult),
                                                         healthcheckSpec.getInitialDelay().toMilliseconds(),
                                                         healthcheckSpec.getInterval().toMilliseconds(),
                                                         TimeUnit.MILLISECONDS);
        checkLock.lock();
        try {
            while (!stop.get() && isHealthy()) {
                try {
                    stateChanged.await();
                }
                catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            if (stop.get()) {
                log.info("Stopping health-checks");
                stopJob();
                return StateData.create(InstanceState.STOPPING, currentState.getData());
            }
            val result = currentResult.get();
            if (null == result || result.getStatus().equals(CheckResult.Status.UNHEALTHY)) {
                stopJob();
                return StateData.errorFrom(
                        currentState,
                        InstanceState.UNHEALTHY,
                        "Healthcheck failed" + (null != result ? ":" + result.getMessage() : ""));
            }
        }
        finally {
            checkLock.unlock();
        }
        return StateData.errorFrom(currentState, InstanceState.UNHEALTHY, "Node is unhealthy");
    }

    private void stopJob() {
        checkerJob.cancel(true);
        executorService.shutdownNow();
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
