package com.phonepe.drove.common;

import com.codahale.metrics.SharedMetricRegistries;
import io.appform.functionmetrics.FunctionMetricsManager;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;


/**
 *
 */
@Slf4j
class StateMachineTest {

    private enum TestSMState {
        WAITING,
        WORKING,
        STOPPED,
        FAILED
    }

    @Value
    private static class TestData {
    }

    @Value
    private static class TestOp {
        TestSMState nextState;
    }

    private static final class TestSMContext extends ActionContext<TestOp> {
    }

    private static class TestAction extends Action<TestData, TestSMState, TestSMContext, TestOp> {

        @Override
        public StateData<TestSMState, TestData> execute(
                TestSMContext context,
                StateData<TestSMState, TestData> currentState) {
            val update = context.getUpdate().orElse(null);
            if (null == update) {
                return StateData.from(currentState, TestSMState.WAITING);
            }
            context.ackUpdate();
            return StateData.from(currentState, update.nextState);
        }

        @Override
        public void stop() {

        }
    }

    private static final class TestSM extends StateMachine<TestData, TestOp, TestSMState, TestSMContext, TestAction> {
        public TestSM(final TestSMContext ctx) {
            super(StateData.create(TestSMState.WAITING, new TestData()),
                    ctx,
                    transition -> new TestAction(),
                    List.of(new Transition<>(TestSMState.WAITING, TestAction.class, TestSMState.WAITING, TestSMState.WORKING),
                            new Transition<>(TestSMState.WORKING, TestAction.class, TestSMState.STOPPED)));
        }
    }

    @Test
    void testNormalExecution() {
        FunctionMetricsManager.initialize("drove.test", SharedMetricRegistries.getOrCreate("test"));
        val ctx = new TestSMContext();
        val tm = new TestSM(ctx);
        val stopped = new AtomicBoolean();
        val states = new LinkedHashSet<TestSMState>();
        tm.onStateChange().connect(sd -> {
            log.info("CURR STATE: {}", sd.getState().name());
            if(sd.getState().equals(TestSMState.STOPPED)) {
                stopped.set(true);
            }
            states.add(sd.getState());
        });
        val l = new ReentrantLock();
        val c = l.newCondition();
        val runner = new TestSMRunner(tm, ctx, Throwable::printStackTrace);
        runner.start();
        runner.sendCommand(TestSMState.WORKING);
        runner.sendCommand(TestSMState.STOPPED);
        await().until(stopped::get);
        assertEquals(Set.of(TestSMState.WAITING, TestSMState.WORKING, TestSMState.STOPPED), states);
        runner.stop();
    }

    @Test
    void testInvalidTransition() {
        FunctionMetricsManager.initialize("drove.test", SharedMetricRegistries.getOrCreate("test"));
        val ctx = new TestSMContext();
        val tm = new TestSM(ctx);
        val failed = new AtomicBoolean();
        val states = new LinkedHashSet<TestSMState>();
        tm.onStateChange().connect(sd -> {
            log.info("CURR STATE: {}", sd.getState().name());
            states.add(sd.getState());
        });
        val runner = new TestSMRunner(tm, ctx, err -> {
            if (err instanceof IllegalStateException) {
                failed.set(true);
            }
        });
        runner.start();

        runner.sendCommand(TestSMState.FAILED);
        await().until(failed::get);
        assertEquals(Set.of(TestSMState.WAITING), states);

    }



    private static class TestSMRunner implements Runnable {
        private final TestSM tm;
        private final ReentrantLock l = new ReentrantLock();
        private final Condition c = l.newCondition();
        private final TestSMContext ctx;
        private final Consumer<Exception> errorHandler;

        private final ExecutorService e = Executors.newSingleThreadExecutor();

        private Future<?> f;

        public TestSMRunner(
                TestSM tm,
                TestSMContext ctx,
                Consumer<Exception> errorHandler) {
            this.tm = tm;
            this.ctx = ctx;
            this.errorHandler = errorHandler;
        }

        void start() {
            this.f = e.submit(this);
        }

        void stop() {
            f.cancel(true);
            e.shutdown();
            try {
                e.awaitTermination(2, TimeUnit.SECONDS);
            }
            catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
            e.shutdownNow();
        }

        @Override
        public void run() {
            try {
                var state = TestSMState.WAITING;
                while (true){
                    state = tm.execute();
                    if(TestSMState.STOPPED.equals(state)) {
                        break;
                    }
                    l.lock();
                    try {
                        while (null == ctx.getUpdate().orElse(null)) {
                            c.await();
                        }
                    }
                    catch (InterruptedException ex) {
                        log.info("state machine interrupted");
                        Thread.currentThread().interrupt();
                    }
                    finally {
                        l.unlock();
                    }
                }
                log.info("state machine completed");
            }
            catch (Exception err) {
                errorHandler.accept(err);
                log.error("state machine failed");
            }
        }

        private void sendCommand(TestSMState state) {
            await().until(() -> {
                val status = tm.notifyUpdate(new TestOp(state));
                if(status) {
                    l.lock();
                    try {
                        c.signalAll();
                    }
                    finally {
                        l.unlock();
                    }
                }
                return status;
            });
        }
    }
}