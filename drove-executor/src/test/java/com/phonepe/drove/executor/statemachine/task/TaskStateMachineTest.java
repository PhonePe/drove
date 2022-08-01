package com.phonepe.drove.executor.statemachine.task;

import com.google.inject.Guice;
import com.google.inject.Stage;
import com.phonepe.drove.common.CommonTestUtils;
import com.phonepe.drove.executor.ExecutorTestingUtils;
import com.phonepe.drove.executor.InjectingTaskActionFactory;
import com.phonepe.drove.executor.model.ExecutorTaskInfo;
import com.phonepe.drove.models.taskinstance.TaskState;
import com.phonepe.drove.statemachine.StateData;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.concurrent.Executors;

import static com.phonepe.drove.models.taskinstance.TaskState.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 *
 */
@Slf4j
class TaskStateMachineTest {

    @Test
    void test() {
        val instanceSpec = ExecutorTestingUtils.testTaskInstanceSpec();
        val sm = new TaskStateMachine(UUID.randomUUID().toString(),
                                                     instanceSpec,
                                                     StateData.create(TaskState.PROVISIONING,
                                                                      new ExecutorTaskInfo(instanceSpec.getTaskId(),
                                                                                           instanceSpec.getSourceAppName(),
                                                                                           instanceSpec.getInstanceId(),
                                                                                           "EX1",
                                                                                           "localhost",
                                                                                           instanceSpec.getExecutable(),
                                                                                           List.of(),
                                                                                           instanceSpec.getVolumes(),
                                                                                           instanceSpec.getLoggingSpec(),
                                                                                           instanceSpec.getEnv(),
                                                                                           Map.of(),
                                                                                           new Date(),
                                                                                           new Date())),
                                                     new InjectingTaskActionFactory(Guice.createInjector(
                                                             Stage.DEVELOPMENT)),
                                                     ExecutorTestingUtils.DOCKER_CLIENT);
        val stateChanges = new HashSet<TaskState>();
        sm.onStateChange().connect(sd -> stateChanges.add(sd.getState()));
        Executors.newSingleThreadExecutor()
                .submit(() -> {
                    try {
                        while (!sm.execute().isTerminal()) {
                            //Wait for changes
                        }
                        log.info("State machine execution completed");
                    }
                    catch (Exception e) {
                        log.error("Error running SM: ", e);
                    }
                });
        CommonTestUtils.waitUntil(() -> stateChanges.contains(TaskState.RUNNING));
        sm.stop();
        CommonTestUtils.waitUntil(() -> stateChanges.contains(STOPPED));
        assertEquals(EnumSet.of(RUNNING, RUN_CANCELLED, STARTING, STOPPED, DEPROVISIONING), stateChanges);
    }

}