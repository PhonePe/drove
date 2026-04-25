/*
 *  Copyright (c) 2024 Original Author(s), PhonePe India Pvt. Ltd.
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

package com.phonepe.drove.statemachine;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

import com.codahale.metrics.SharedMetricRegistries;

import org.junit.jupiter.api.Test;

import io.appform.functionmetrics.FunctionMetricsManager;
import lombok.SneakyThrows;
import lombok.Value;
import lombok.val;
import lombok.extern.slf4j.Slf4j;

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

    private interface TestSMAction extends Action<TestData, TestSMState, TestSMContext, TestOp> {}
    private static class TestAction implements TestSMAction {

        @Override
        public StateData<TestSMState, TestData> execute(
                TestSMContext context,
                StateData<TestSMState, TestData> currentState) {
            val update = context.getUpdate().orElse(null);
            if (null == update) {
                return currentState;
            }
            val ackStatus = context.ackUpdate();
            log.debug("Ack state: {}", ackStatus);
            return StateData.from(currentState, update.nextState);
        }

        @Override
        public void stop() {

        }
    }

    private static class TestStopAction implements TestSMAction {

        private boolean stopCalled;
        private final Lock checkLock = new ReentrantLock();
        private final Condition checkCondition = checkLock.newCondition();
        @Override
        public StateData<TestSMState, TestData> execute(
                TestSMContext context,
                StateData<TestSMState, TestData> currentState) {
            checkLock.lock();
            try {
                log.info("Waiting for stop to be called");
                while (!stopCalled) {
                    checkCondition.await();
                }
            }
            catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return StateData.errorFrom(currentState, TestSMState.FAILED, "Action interrupted");
            }
            finally {
                checkLock.unlock();
            }
            return StateData.from(currentState, TestSMState.STOPPED);

        }

        @Override
        public void stop() {
            checkLock.lock();
            try {
                stopCalled = true;
                checkCondition.signalAll();
            }
            finally {
                checkLock.unlock();
            }
        }
    }

    private static final class TestActionFactory implements ActionFactory<TestData, TestOp, TestSMState, TestSMContext, TestSMAction> {

        @Override
        @SneakyThrows
        public TestSMAction create(Transition<TestData, TestOp, TestSMState, TestSMContext, TestSMAction> transition) {
            return transition.getAction().getDeclaredConstructor().newInstance();
        }
    }
    private static final class TestSM extends StateMachine<TestData, TestOp, TestSMState, TestSMContext, TestSMAction> {
        public TestSM(final TestSMContext ctx) {
            super(StateData.create(TestSMState.WAITING, new TestData()),
                  ctx,
                  new TestActionFactory(),
                  List.of(
                          new Transition<>(TestSMState.WAITING, TestAction.class, TestSMState.WAITING, TestSMState.WORKING),
                          new Transition<>(TestSMState.WORKING, TestStopAction.class, TestSMState.STOPPED)));
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
        val runner = new TestSMRunner(tm, ctx, Throwable::printStackTrace);
        runner.start();
        runner.sendCommand(TestSMState.WORKING);
        runner.sendCommand(TestSMState.WORKING); //Dummy just to wake up the executor
        await().until(() -> null != tm.currentAction().orElse(null)); //Wait for stop action to start listening
        runner.stop(); //Stop called here which will trigger the condition in TestStopAction
        await().forever().until(stopped::get);
        assertEquals(Set.of(TestSMState.WAITING, TestSMState.WORKING, TestSMState.STOPPED), states);
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

    @Test
    void testDirectStateChange() {
        FunctionMetricsManager.initialize("drove.test", SharedMetricRegistries.getOrCreate("test"));
        val ctx = new TestSMContext();
        val tm = new TestSM(ctx);
        val states = new LinkedHashSet<TestSMState>();
        tm.onStateChange().connect(sd -> {
            log.info("CURR STATE: {}", sd.getState().name());
            states.add(sd.getState());
        });

        val newStateData = StateData.from(tm.getCurrentState(), TestSMState.WORKING);
        val resultState = tm.changeState(newStateData);

        assertEquals(TestSMState.WORKING, resultState);
        assertEquals(TestSMState.WORKING, tm.getCurrentState().getState());
        assertEquals(Set.of(TestSMState.WORKING), states);
    }

    @Test
    void testChangeStateWithErrorData() {
        FunctionMetricsManager.initialize("drove.test", SharedMetricRegistries.getOrCreate("test"));
        val ctx = new TestSMContext();
        val tm = new TestSM(ctx);
        val states = new LinkedHashSet<TestSMState>();
        tm.onStateChange().connect(sd -> {
            log.info("CURR STATE: {}, Error: {}", sd.getState().name(), sd.getError());
            states.add(sd.getState());
        });

        val errorStateData = StateData.errorFrom(tm.getCurrentState(), TestSMState.FAILED, "Test error message");
        val resultState = tm.changeState(errorStateData);

        assertEquals(TestSMState.FAILED, resultState);
        assertEquals(TestSMState.FAILED, tm.getCurrentState().getState());
        assertFalse(tm.getCurrentState().getError().isEmpty());
        assertEquals("Test error message", tm.getCurrentState().getError());
        assertEquals(Set.of(TestSMState.FAILED), states);
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

        @SneakyThrows
        void stop() {
            tm.stop();
            f.get();
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
            await().forever().until(() -> {
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
