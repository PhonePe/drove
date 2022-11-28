package com.phonepe.drove.executor.statemachine.application.actions;

import com.phonepe.drove.common.model.ApplicationInstanceSpec;
import com.phonepe.drove.executor.checker.Checker;
import com.phonepe.drove.executor.model.ExecutorInstanceInfo;
import com.phonepe.drove.executor.statemachine.application.ApplicationInstanceAction;
import com.phonepe.drove.executor.statemachine.InstanceActionContext;
import com.phonepe.drove.executor.utils.ExecutorUtils;
import com.phonepe.drove.models.application.CheckResult;
import com.phonepe.drove.models.instance.InstanceState;
import io.appform.functionmetrics.MonitoredFunction;
import com.phonepe.drove.statemachine.StateData;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.slf4j.MDC;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 *
 */
@Slf4j
public class ApplicationInstanceHealthcheckAction extends ApplicationInstanceAction {
    private final ScheduledExecutorService executorService = Executors.newSingleThreadScheduledExecutor();

    private final Lock checkLock = new ReentrantLock();
    private final Condition stateChanged = checkLock.newCondition();
    private final AtomicReference<CheckResult> currentResult = new AtomicReference<>(null);
    private final AtomicBoolean stop = new AtomicBoolean();
    private ScheduledFuture<?> checkerJob;

    @Override
    @MonitoredFunction(method = "execute")
    protected StateData<InstanceState, ExecutorInstanceInfo> executeImpl(
            InstanceActionContext<ApplicationInstanceSpec> context, StateData<InstanceState, ExecutorInstanceInfo> currentState) {
        val healthcheckSpec = context.getInstanceSpec().getHealthcheck();
        val checker = ExecutorUtils.createChecker(context, currentState.getData(), healthcheckSpec);
        try {
            val currentContext = MDC.getCopyOfContextMap();
            val mdc = null != currentContext
                      ? currentContext
                      : Collections.<String, String>emptyMap();
            checkerJob = executorService.scheduleWithFixedDelay(new HealthChecker(checker,
                                                                                  checkLock,
                                                                                  stateChanged,
                                                                                  healthcheckSpec.getAttempts(),
                                                                                  currentResult,
                                                                                  mdc),
                                                                healthcheckSpec.getInitialDelay().toMilliseconds(),
                                                                healthcheckSpec.getInterval().toMilliseconds(),
                                                                TimeUnit.MILLISECONDS);
            checkLock.lock();
            monitor();
            if (stop.get()) {
                log.info("Stopping health-checks");
                return StateData.from(currentState, InstanceState.STOPPING);
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
            log.error("Error occurred: ", e);
        }
        finally {
            stopJob();
            checkLock.unlock();
        }
        return StateData.errorFrom(currentState, InstanceState.UNHEALTHY, "Node is unhealthy");
    }

    @Override
    protected InstanceState defaultErrorState() {
        return InstanceState.UNHEALTHY;
    }

    private void monitor() {
        while (!stop.get() && isHealthy()) {
            try {
                stateChanged.await();
            }
            catch (InterruptedException e) {
                log.info("Health check monitor thread interrupted.");
                Thread.currentThread().interrupt();
                return;
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
        private final int maxAttempts;
        private final AtomicReference<CheckResult> currentResult;
        private final Map<String, String> mdc;
        private int attemptCount = 1;

        private HealthChecker(
                Checker checker,
                Lock lock,
                Condition condition,
                int maxAttempts,
                AtomicReference<CheckResult> currentResult,
                Map<String, String> mdc) {
            this.checker = checker;
            this.lock = lock;
            this.condition = condition;
            this.maxAttempts = maxAttempts;
            this.currentResult = currentResult;
            this.mdc = mdc;
        }

        @Override
        @MonitoredFunction(method = "execute")
        public void run() {
            MDC.setContextMap(mdc);
            lock.lock();
            try {
                log.debug("Starting healthcheck call");
                val result = checker.call();
                if (result.getStatus().equals(CheckResult.Status.UNHEALTHY) && attemptCount < maxAttempts) {
                    log.warn("Health check returned unhealthy for attempt {}", attemptCount);
                    attemptCount++;
                    return;
                }
                if(result.getStatus().equals(CheckResult.Status.UNHEALTHY)) {
                    log.warn("Healthcheck returned unhealthy. {}", result);
                }
                else {
                    log.debug("Health check results: {}", result);
                    attemptCount = 0;
                }
                currentResult.set(result);
            }
            catch (Exception e) {
                currentResult.set(new CheckResult(CheckResult.Status.UNHEALTHY, "Error running healthcheck: " + e));
            }
            finally {
                condition.signalAll();
                lock.unlock();
                MDC.clear();
            }
        }
    }
}
