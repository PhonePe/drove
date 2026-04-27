/*
 *  Copyright (c) 2024 Original Author(s), PhonePe India Pvt. Ltd.
 *
 *  Licensed under the Apache License, Version 2.0 (the \"License\");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an \"AS IS\" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package com.phonepe.drove.executor.engine;

import com.codahale.metrics.SharedMetricRegistries;
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Provides;
import com.phonepe.drove.auth.config.ClusterAuthenticationConfig;
import com.phonepe.drove.common.AbstractTestBase;
import com.phonepe.drove.common.CommonTestUtils;
import com.phonepe.drove.common.CommonUtils;
import com.phonepe.drove.common.discovery.leadership.LeadershipObserver;
import com.phonepe.drove.common.discovery.leadership.ZkLeadershipObserver;
import com.phonepe.drove.common.model.MessageDeliveryStatus;
import com.phonepe.drove.common.model.MessageHeader;
import com.phonepe.drove.common.model.TaskInstanceSpec;
import com.phonepe.drove.common.model.executor.ExecutorAddress;
import com.phonepe.drove.common.model.executor.StartTaskMessage;
import com.phonepe.drove.common.net.HttpCaller;
import com.phonepe.drove.executor.ExecutorOptions;
import com.phonepe.drove.executor.ExecutorTestingUtils;
import com.phonepe.drove.executor.InjectingTaskActionFactory;
import com.phonepe.drove.executor.managed.ExecutorIdManager;
import com.phonepe.drove.executor.managed.ExecutorStateManager;
import com.phonepe.drove.executor.model.ExecutorTaskInfo;
import com.phonepe.drove.executor.resourcemgmt.ResourceManager;
import com.phonepe.drove.executor.statemachine.ExecutorActionBase;
import com.phonepe.drove.executor.statemachine.InstanceActionContext;
import com.phonepe.drove.executor.statemachine.task.TaskAction;
import com.phonepe.drove.executor.utils.ExecutorUtils;
import com.phonepe.drove.models.info.nodedata.NodeTransportType;
import com.phonepe.drove.models.taskinstance.TaskInfo;
import com.phonepe.drove.models.taskinstance.TaskState;
import com.phonepe.drove.statemachine.StateData;
import com.phonepe.drove.statemachine.Transition;
import io.dropwizard.lifecycle.setup.LifecycleEnvironment;
import io.dropwizard.setup.Environment;
import lombok.val;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import java.util.HashSet;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static com.phonepe.drove.common.CommonTestUtils.waitUntil;
import static com.phonepe.drove.models.taskinstance.TaskState.LOST;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Test to verify exception handling in InstanceEngine.java around line 176-181.
 * This test covers the catch clause that handles exceptions during stateMachine.execute()
 * and transitions tasks to LOST state via StateData.errorFrom.
 */
class TaskInstanceEngineStateMachineErrorTest extends AbstractTestBase {
    @Inject
    protected ResourceManager resourceDB;

    @Inject
    protected ExecutorStateManager executorStateManager;

    @Inject
    protected TaskInstanceEngine taskInstanceEngine;

    @Inject
    protected ApplicationInstanceEngine applicationInstanceEngine;

    @Inject
    protected LocalServiceInstanceEngine localServiceInstanceEngine;

    /**
     * A failing task action that returns an invalid state to trigger IllegalStateException
     * in StateMachine.execute() at line 107 when it validates the transition.
     */
    public static class FailingTaskAction extends TaskAction {
        private static final AtomicBoolean shouldFail = new AtomicBoolean(true);

        @Override
        protected StateData<TaskState, ExecutorTaskInfo> executeImpl(
                InstanceActionContext<TaskInstanceSpec> context,
                StateData<TaskState, ExecutorTaskInfo> currentState) {
            if (shouldFail.get()) {
                // Return RUNNING state which is NOT a valid transition from PENDING
                // This will cause StateMachine.execute() to throw IllegalStateException
                // which will be caught by the InstanceEngine catch clause at line 176-181
                return StateData.from(currentState, TaskState.RUNNING);
            }
            return StateData.from(currentState, TaskState.PROVISIONING);
        }

        @Override
        protected TaskState defaultErrorState() {
            return TaskState.STOPPED;
        }

        @Override
        public void stop() {
            // Ignore
        }

        public static void enableFailure() {
            shouldFail.set(true);
        }

        public static void disableFailure() {
            shouldFail.set(false);
        }
    }

    /**
     * Custom action factory that returns our failing action
     */
    public static class FailingTaskActionFactory extends InjectingTaskActionFactory {
        public FailingTaskActionFactory(Injector injector) {
            super(injector);
        }

        @Override
        public ExecutorActionBase<ExecutorTaskInfo, TaskState, TaskInstanceSpec> create(
                Transition<ExecutorTaskInfo, Void, TaskState, InstanceActionContext<TaskInstanceSpec>,
                        ExecutorActionBase<ExecutorTaskInfo, TaskState, TaskInstanceSpec>> transition) {
            // Return failing action for the first transition (PENDING -> PROVISIONING)
            if (transition.getFrom() == TaskState.PENDING) {
                return new FailingTaskAction();
            }
            return super.create(transition);
        }
    }

    @BeforeEach
    void setup() {
        FailingTaskAction.enableFailure();
        
        val injector = Guice.createInjector(new AbstractModule() {
            @Override
            protected void configure() {
                super.configure();
            }

            @Provides
            @Singleton
            public Environment environment() {
                val env = mock(Environment.class);
                val lsm = new LifecycleEnvironment(SharedMetricRegistries.getOrCreate("test"));
                when(env.lifecycle()).thenReturn(lsm);
                return env;
            }

            @Provides
            @Singleton
            public ExecutorOptions executorOptions() {
                return ExecutorOptions.DEFAULT.withCacheImages(true);
            }

            @Provides
            @Singleton
            public ApplicationInstanceEngine applicationInstanceEngine(
                    final Injector injector,
                    final ResourceManager resourceDB,
                    final ExecutorIdManager executorIdManager) {
                val executorService = Executors.newSingleThreadExecutor();
                return new ApplicationInstanceEngine(
                        executorIdManager,
                        executorService,
                        new com.phonepe.drove.executor.InjectingApplicationInstanceActionFactory(injector),
                        resourceDB,
                        ExecutorTestingUtils.DOCKER_CLIENT);
            }

            @Provides
            @Singleton
            public TaskInstanceEngine taskInstanceEngine(
                    final Injector injector,
                    final ResourceManager resourceDB,
                    final ExecutorIdManager executorIdManager) {
                val executorService = Executors.newSingleThreadExecutor();
                // Use our custom failing action factory
                return new TaskInstanceEngine(
                        executorIdManager,
                        executorService,
                        new FailingTaskActionFactory(injector),
                        resourceDB,
                        ExecutorTestingUtils.DOCKER_CLIENT);
            }

            @Provides
            @Singleton
            public LocalServiceInstanceEngine localServiceInstanceEngine(
                    final Injector injector,
                    final ResourceManager resourceDB,
                    final ExecutorIdManager executorIdManager) {
                val executorService = Executors.newSingleThreadExecutor();
                return new LocalServiceInstanceEngine(
                        executorIdManager,
                        executorService,
                        new com.phonepe.drove.executor.InjectingLocalServiceInstanceActionFactory(injector),
                        resourceDB,
                        ExecutorTestingUtils.DOCKER_CLIENT);
            }

            @Provides
            @Singleton
            public HttpCaller httpCaller() {
                return CommonTestUtils.httpCaller();
            }

            @Provides
            @Singleton
            public ClusterAuthenticationConfig clusterAuthenticationConfig() {
                return ClusterAuthenticationConfig.DEFAULT;
            }

            @Provides
            @Singleton
            public LeadershipObserver leadershipObserver() {
                return mock(ZkLeadershipObserver.class);
            }

            @Provides
            @Singleton
            @Named("ControllerHttpClient")
            public CloseableHttpClient closeableHttpClient() {
                return ExecutorUtils.buildControllerClient(ClusterAuthenticationConfig.DEFAULT);
            }
        });

        injector.injectMembers(this);
        resourceDB.populateResources(Map.of(0, ResourceManager.NodeInfo.from(
                IntStream.rangeClosed(0, 7).boxed().collect(Collectors.toUnmodifiableSet()),
                16_000_000)));
    }

    @Test
    void testStateMachineExecutionErrorTriggersLostState() {
        // This test verifies the catch clause in InstanceEngine.java (line 176-181)
        // When stateMachine.execute() throws an exception (e.g., IllegalStateException
        // for invalid state transitions), the catch block should:
        // 1. Call stateMachine.changeState with StateData.errorFrom
        // 2. Transition to LOST state (via lostState())
        // 3. Log the error
        //
        // The action returns RUNNING from PENDING state, which is invalid according to
        // TaskStateMachine transitions (PENDING can only go to PROVISIONING or STOPPED).
        // This causes StateMachine.execute() to throw IllegalStateException at line 107.

        val spec = ExecutorTestingUtils.testTaskInstanceSpec();
        val instanceId = CommonUtils.instanceId(spec);
        val stateChanges = new HashSet<TaskState>();
        val lostStateDetected = new AtomicBoolean(false);
        
        taskInstanceEngine.onStateChange().connect(state -> {
            if (state.getInstanceId().equals(instanceId)) {
                stateChanges.add(state.getState());
                if (state.getState().equals(LOST)) {
                    lostStateDetected.set(true);
                }
            }
        });
        
        val executorAddress = new ExecutorAddress("eid", "localhost", 3000, NodeTransportType.HTTP);
        val startInstanceMessage = new StartTaskMessage(
                MessageHeader.controllerRequest(),
                executorAddress,
                spec);
        val messageHandler = new ExecutorMessageHandler(
                applicationInstanceEngine,
                taskInstanceEngine,
                localServiceInstanceEngine,
                executorStateManager);
        
        val startResponse = startInstanceMessage.accept(messageHandler);
        assertEquals(MessageDeliveryStatus.ACCEPTED, startResponse.getStatus(), 
                "Task should be accepted for execution");
        
        // Wait for the LOST state to be detected by our callback
        // Since LOST is a terminal state, the instance gets cleaned up immediately,
        // so we can't query currentState() - we must rely on the callback
        waitUntil(() -> lostStateDetected.get());
        
        // Verify that LOST state was reached (this confirms the catch clause was executed)
        assertTrue(stateChanges.contains(LOST), 
                "Task should have transitioned to LOST state when stateMachine.execute() threw IllegalStateException");
        
        assertTrue(lostStateDetected.get(),
                "LOST state should have been detected, confirming StateData.errorFrom was called in the catch clause");
    }
}
