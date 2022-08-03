package com.phonepe.drove.controller.engine;

import com.phonepe.drove.common.CommonTestUtils;
import com.phonepe.drove.common.model.MessageDeliveryStatus;
import com.phonepe.drove.common.model.MessageResponse;
import com.phonepe.drove.common.model.TaskInstanceSpec;
import com.phonepe.drove.common.model.executor.StartTaskMessage;
import com.phonepe.drove.common.model.executor.StopTaskMessage;
import com.phonepe.drove.controller.ControllerTestBase;
import com.phonepe.drove.controller.ControllerTestUtils;
import com.phonepe.drove.controller.resourcemgmt.DefaultInstanceScheduler;
import com.phonepe.drove.controller.resourcemgmt.InMemoryClusterResourcesDB;
import com.phonepe.drove.controller.testsupport.InMemoryApplicationInstanceInfoDB;
import com.phonepe.drove.controller.testsupport.InMemoryTaskDB;
import com.phonepe.drove.jobexecutor.JobExecutor;
import com.phonepe.drove.models.operation.ClusterOpSpec;
import com.phonepe.drove.models.operation.deploy.FailureStrategy;
import com.phonepe.drove.models.operation.taskops.TaskCreateOperation;
import com.phonepe.drove.models.operation.taskops.TaskKillOperation;
import com.phonepe.drove.models.taskinstance.TaskInfo;
import com.phonepe.drove.models.taskinstance.TaskResult;
import io.appform.signals.signals.ConsumingFireForgetSignal;
import io.dropwizard.util.Duration;
import lombok.SneakyThrows;
import lombok.val;
import org.junit.jupiter.api.Test;

import java.util.Date;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.IntStream;

import static com.phonepe.drove.controller.ControllerTestUtils.taskSpec;
import static com.phonepe.drove.models.taskinstance.TaskState.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 *
 */
class TaskRunnerTest extends ControllerTestBase {

    @Test
    @SneakyThrows
    void testTaskRun() {
        val tdb = new InMemoryTaskDB();
        val instanceDB = new InMemoryApplicationInstanceInfoDB();
        val cdb = new InMemoryClusterResourcesDB();
        cdb.update(IntStream.rangeClosed(1, 5).mapToObj(ControllerTestUtils::generateExecutorNode).toList());
        val inst = new AtomicReference<TaskInstanceSpec>();

        val comm = mock(ControllerCommunicator.class);
        when(comm.send(any(StartTaskMessage.class))).thenAnswer(invocationOnMock -> {
            val msg = invocationOnMock.getArgument(0, StartTaskMessage.class);
            inst.set(msg.getSpec());
            return new MessageResponse(msg.getHeader(), MessageDeliveryStatus.ACCEPTED);
        });
        val stopCalled = new AtomicBoolean();
        when(comm.send(any(StopTaskMessage.class))).thenAnswer(invocationOnMock -> {
            val msg = invocationOnMock.getArgument(0, StopTaskMessage.class);
            stopCalled.set(true);
            return new MessageResponse(msg.getHeader(), MessageDeliveryStatus.ACCEPTED);
        });
        val completedSignal = new ConsumingFireForgetSignal<TaskRunner>();
        val taskSpec = taskSpec();
        val tr = new TaskRunner(taskSpec.getSourceAppName(),
                                taskSpec.getTaskId(),
                                new JobExecutor<>(Executors.newSingleThreadExecutor()),
                                tdb,
                                cdb,
                                new DefaultInstanceScheduler(instanceDB, tdb, cdb),
                                comm,
                                new DefaultControllerRetrySpecFactory(),
                                new RandomInstanceIdGenerator(),
                                Executors.defaultThreadFactory(),
                                completedSignal);
        val testDone = new AtomicBoolean();
        completedSignal.connect(runner -> testDone.set(true));
        tdb.updateTask(taskSpec.getSourceAppName(), taskSpec.getTaskId(), new TaskInfo(taskSpec.getSourceAppName(),
                                                                                       taskSpec.getTaskId(),
                                                                                       "TI001",
                                                                                       "EXECUTOR_5",
                                                                                       "localhost",
                                                                                       taskSpec.getExecutable(),
                                                                                       null,
                                                                                       taskSpec.getVolumes(),
                                                                                       taskSpec.getLogging(),
                                                                                       taskSpec.getEnv(),
                                                                                       RUNNING,
                                                                                       Map.of(),
                                                                                       null,
                                                                                       "",
                                                                                       new Date(),
                                                                                       new Date()));
        tr.startTask(new TaskCreateOperation(taskSpec, ClusterOpSpec.DEFAULT));
        val f = Executors.newSingleThreadExecutor().submit(tr);

        CommonTestUtils.waitUntil(() -> inst.get() != null);
        //Start message has been sent, now kill the task
        tr.stopTask(new TaskKillOperation(taskSpec.getSourceAppName(), taskSpec.getTaskId(), ClusterOpSpec.DEFAULT));

        CommonTestUtils.waitUntil(stopCalled::get);
        //Stop has been called, so set state accordingly
        tdb.updateTask(taskSpec.getSourceAppName(), taskSpec.getTaskId(), new TaskInfo(taskSpec.getSourceAppName(),
                                                                                       taskSpec.getTaskId(),
                                                                                       "TI001",
                                                                                       "EXECUTOR_5",
                                                                                       "localhost",
                                                                                       taskSpec.getExecutable(),
                                                                                       inst.get().getResources(),
                                                                                       taskSpec.getVolumes(),
                                                                                       taskSpec.getLogging(),
                                                                                       taskSpec.getEnv(),
                                                                                       STOPPED,
                                                                                       Map.of(),
                                                                                       new TaskResult(TaskResult.Status.SUCCESSFUL, 0),
                                                                                       "",
                                                                                       new Date(),
                                                                                       new Date()));
        tr.updateCurrentState();
        CommonTestUtils.waitUntil(testDone::get);
        assertEquals(STOPPED, tdb.task(taskSpec.getSourceAppName(), taskSpec.getTaskId())
                .map(TaskInfo::getState)
                .orElse(UNKNOWN));
        f.get();
    }

    @Test
    @SneakyThrows
    void testTaskRunFail() {
        val tdb = new InMemoryTaskDB();
        val instanceDB = new InMemoryApplicationInstanceInfoDB();
        val cdb = new InMemoryClusterResourcesDB();
        cdb.update(IntStream.rangeClosed(1, 5).mapToObj(ControllerTestUtils::generateExecutorNode).toList());

        val comm = mock(ControllerCommunicator.class);
        when(comm.send(any(StartTaskMessage.class))).thenAnswer(invocationOnMock -> {
            val msg = invocationOnMock.getArgument(0, StartTaskMessage.class);
            return new MessageResponse(msg.getHeader(), MessageDeliveryStatus.REJECTED);
        });

        val completedSignal = new ConsumingFireForgetSignal<TaskRunner>();
        val taskSpec = taskSpec();
        val tr = new TaskRunner(taskSpec.getSourceAppName(),
                                taskSpec.getTaskId(),
                                new JobExecutor<>(Executors.newSingleThreadExecutor()),
                                tdb,
                                cdb,
                                new DefaultInstanceScheduler(instanceDB, tdb, cdb),
                                comm,
                                new DefaultControllerRetrySpecFactory(),
                                new RandomInstanceIdGenerator(),
                                Executors.defaultThreadFactory(),
                                completedSignal);
        val testDone = new AtomicBoolean();
        completedSignal.connect(runner -> testDone.set(true));

        tr.startTask(new TaskCreateOperation(taskSpec,
                                             new ClusterOpSpec(Duration.seconds(1), 1, FailureStrategy.STOP)));
        val f = Executors.newSingleThreadExecutor().submit(tr);

        CommonTestUtils.waitUntil(testDone::get);
        assertTrue(tdb.task(taskSpec.getSourceAppName(), taskSpec.getTaskId()).isEmpty());
        f.get();
    }
}