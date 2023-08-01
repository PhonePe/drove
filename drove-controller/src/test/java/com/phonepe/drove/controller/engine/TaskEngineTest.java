package com.phonepe.drove.controller.engine;

import com.phonepe.drove.common.CommonTestUtils;
import com.phonepe.drove.common.model.MessageDeliveryStatus;
import com.phonepe.drove.common.model.MessageResponse;
import com.phonepe.drove.common.model.TaskInstanceSpec;
import com.phonepe.drove.common.model.executor.StartTaskMessage;
import com.phonepe.drove.common.model.executor.StopTaskMessage;
import com.phonepe.drove.controller.ControllerTestBase;
import com.phonepe.drove.controller.ControllerTestUtils;
import com.phonepe.drove.controller.managed.LeadershipEnsurer;
import com.phonepe.drove.controller.resourcemgmt.DefaultInstanceScheduler;
import com.phonepe.drove.controller.resourcemgmt.InMemoryClusterResourcesDB;
import com.phonepe.drove.controller.statedb.TaskDB;
import com.phonepe.drove.controller.testsupport.InMemoryApplicationInstanceInfoDB;
import com.phonepe.drove.controller.testsupport.InMemoryClusterStateDB;
import com.phonepe.drove.controller.testsupport.InMemoryTaskDB;
import com.phonepe.drove.jobexecutor.JobExecutor;
import com.phonepe.drove.jobexecutor.JobTopology;
import com.phonepe.drove.models.info.ExecutorResourceSnapshot;
import com.phonepe.drove.models.info.nodedata.ExecutorNodeData;
import com.phonepe.drove.models.info.nodedata.NodeTransportType;
import com.phonepe.drove.models.info.resources.available.AvailableCPU;
import com.phonepe.drove.models.info.resources.available.AvailableMemory;
import com.phonepe.drove.models.operation.taskops.TaskCreateOperation;
import com.phonepe.drove.models.operation.taskops.TaskKillOperation;
import com.phonepe.drove.models.taskinstance.TaskInfo;
import com.phonepe.drove.models.taskinstance.TaskResult;
import io.appform.signals.signals.ConsumingFireForgetSignal;
import lombok.val;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.IntStream;

import static com.phonepe.drove.controller.ControllerTestUtils.taskSpec;
import static com.phonepe.drove.models.taskinstance.TaskState.RUNNING;
import static com.phonepe.drove.models.taskinstance.TaskState.STOPPED;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 *
 */
class TaskEngineTest extends ControllerTestBase {

    @Test
    void testRunStartStop() {
        val tdb = new InMemoryTaskDB();
        val instanceDB = new InMemoryApplicationInstanceInfoDB();
        val cdb = new InMemoryClusterResourcesDB();
        cdb.update(IntStream.rangeClosed(1, 5).mapToObj(ControllerTestUtils::generateExecutorNode).toList());
        val le = mock(LeadershipEnsurer.class);
        when(le.isLeader()).thenReturn(true);

        val inst = new AtomicReference<TaskInstanceSpec>();

        val comm = mock(ControllerCommunicator.class);
        when(comm.send(any(StartTaskMessage.class))).thenAnswer(invocationOnMock -> {
            val msg = invocationOnMock.getArgument(0, StartTaskMessage.class);
            val instSpec = msg.getSpec();
            inst.set(instSpec);
            tdb.updateTask(instSpec.getSourceAppName(),
                           instSpec.getTaskId(),
                           new TaskInfo(instSpec.getSourceAppName(),
                                        instSpec.getTaskId(),
                                        instSpec.getInstanceId(),
                                        "EXECUTOR_5",
                                        "localhost",
                                        instSpec.getExecutable(),
                                        instSpec.getResources(),
                                        instSpec.getVolumes(),
                                        instSpec.getLoggingSpec(),
                                        instSpec.getEnv(),
                                        RUNNING,
                                        Map.of(),
                                        null,
                                        "",
                                        new Date(),
                                        new Date()));
            return new MessageResponse(msg.getHeader(), MessageDeliveryStatus.ACCEPTED);
        });
        when(comm.send(any(StopTaskMessage.class))).thenAnswer(invocationOnMock -> {
            val msg = invocationOnMock.getArgument(0, StopTaskMessage.class);
            val instSpec = inst.get();
            tdb.updateTask(instSpec.getSourceAppName(),
                           instSpec.getTaskId(),
                           new TaskInfo(instSpec.getSourceAppName(),
                                        instSpec.getTaskId(),
                                        instSpec.getInstanceId(),
                                        "EXECUTOR_5",
                                        "localhost",
                                        instSpec.getExecutable(),
                                        instSpec.getResources(),
                                        instSpec.getVolumes(),
                                        instSpec.getLoggingSpec(),
                                        instSpec.getEnv(),
                                        STOPPED,
                                        Map.of(),
                                        new TaskResult(TaskResult.Status.SUCCESSFUL, 0),
                                        "",
                                        new Date(),
                                        new Date()));
            return new MessageResponse(msg.getHeader(), MessageDeliveryStatus.ACCEPTED);
        });
        val taskSpec = taskSpec();
        val executor = Executors.newCachedThreadPool();
        val te = new TaskEngine(tdb,
                                cdb,
                                new DefaultInstanceScheduler(instanceDB, tdb, cdb),
                                comm,
                                new DefaultControllerRetrySpecFactory(),
                                new RandomInstanceIdGenerator(),
                                Executors.defaultThreadFactory(),
                                executor,
                                new JobExecutor<>(executor),
                                new InMemoryClusterStateDB(),
                                le, ControllerTestUtils.DEFAULT_CLUSTER_OP);

        te.handleTaskOp(new TaskCreateOperation(taskSpec, ControllerTestUtils.DEFAULT_CLUSTER_OP));
        CommonTestUtils.waitUntil(() -> !te.activeTasks().isEmpty());
        assertFalse(te.activeTasks().isEmpty());
        val task = te.activeTasks().get(0);
        te.handleTaskOp(new TaskKillOperation(task.getSourceAppName(), task.getTaskId(), ControllerTestUtils.DEFAULT_CLUSTER_OP));
        CommonTestUtils.waitUntil(() -> te.activeTasks().isEmpty());
        assertTrue(te.activeTasks().isEmpty());
    }

    @Test
    void testRunAutoStop() {
        val tdb = new InMemoryTaskDB();
        val instanceDB = new InMemoryApplicationInstanceInfoDB();
        val cdb = new InMemoryClusterResourcesDB();
        cdb.update(IntStream.rangeClosed(1, 5).mapToObj(ControllerTestUtils::generateExecutorNode).toList());
        val le = mock(LeadershipEnsurer.class);
        when(le.isLeader()).thenReturn(true);

        val comm = mock(ControllerCommunicator.class);
        when(comm.send(any(StartTaskMessage.class))).thenAnswer(invocationOnMock -> {
            val msg = invocationOnMock.getArgument(0, StartTaskMessage.class);
            val instSpec = msg.getSpec();
            tdb.updateTask(instSpec.getSourceAppName(),
                           instSpec.getTaskId(),
                           new TaskInfo(instSpec.getSourceAppName(),
                                        instSpec.getTaskId(),
                                        instSpec.getInstanceId(),
                                        "EXECUTOR_5",
                                        "localhost",
                                        instSpec.getExecutable(),
                                        instSpec.getResources(),
                                        instSpec.getVolumes(),
                                        instSpec.getLoggingSpec(),
                                        instSpec.getEnv(),
                                        RUNNING,
                                        Map.of(),
                                        null,
                                        "",
                                        new Date(),
                                        new Date()));
            return new MessageResponse(msg.getHeader(), MessageDeliveryStatus.ACCEPTED);
        });
        val taskSpec = taskSpec();
        val executor = Executors.newCachedThreadPool();
        val te = new TaskEngine(tdb,
                                cdb,
                                new DefaultInstanceScheduler(instanceDB, tdb, cdb),
                                comm,
                                new DefaultControllerRetrySpecFactory(),
                                new RandomInstanceIdGenerator(),
                                Executors.defaultThreadFactory(),
                                executor,
                                new JobExecutor<>(executor),
                                new InMemoryClusterStateDB(),
                                le, ControllerTestUtils.DEFAULT_CLUSTER_OP);

        te.handleTaskOp(new TaskCreateOperation(taskSpec, ControllerTestUtils.DEFAULT_CLUSTER_OP));
        CommonTestUtils.waitUntil(() -> !te.activeTasks().isEmpty());
        assertFalse(te.activeTasks().isEmpty());
        val task = te.activeTasks().get(0);
        //This simulates task completion
        tdb.updateTask(task.getSourceAppName(),
                       task.getTaskId(),
                       new TaskInfo(task.getSourceAppName(),
                                    task.getTaskId(),
                                    task.getInstanceId(),
                                    task.getExecutorId(),
                                    task.getHostname(),
                                    task.getExecutable(),
                                    task.getResources(),
                                    task.getVolumes(),
                                    task.getLoggingSpec(),
                                    task.getEnv(),
                                    STOPPED,
                                    task.getMetadata(),
                                    new TaskResult(TaskResult.Status.SUCCESSFUL, 0),
                                    "",
                                    task.getCreated(),
                                    new Date()));
        CommonTestUtils.waitUntil(() -> te.activeTasks().isEmpty());
        assertTrue(te.activeTasks().isEmpty());
    }

    @Test
    void testRunTaskLoss() {
        val tdb = new InMemoryTaskDB();
        val instanceDB = new InMemoryApplicationInstanceInfoDB();
        val cdb = new InMemoryClusterResourcesDB();
        cdb.update(IntStream.rangeClosed(1, 5).mapToObj(ControllerTestUtils::generateExecutorNode).toList());
        val le = mock(LeadershipEnsurer.class);
        when(le.isLeader()).thenReturn(true);

        val comm = mock(ControllerCommunicator.class);
        val oldDate = new Date(new Date().getTime() - 100_000L);
        when(comm.send(any(StartTaskMessage.class))).thenAnswer(invocationOnMock -> {
            val msg = invocationOnMock.getArgument(0, StartTaskMessage.class);
            val instSpec = msg.getSpec();
            tdb.updateTask(instSpec.getSourceAppName(),
                           instSpec.getTaskId(),
                           new TaskInfo(instSpec.getSourceAppName(),
                                        instSpec.getTaskId(),
                                        instSpec.getInstanceId(),
                                        "EXECUTOR_5",
                                        "localhost",
                                        instSpec.getExecutable(),
                                        instSpec.getResources(),
                                        instSpec.getVolumes(),
                                        instSpec.getLoggingSpec(),
                                        instSpec.getEnv(),
                                        RUNNING,
                                        Map.of(),
                                        null,
                                        "",
                                        new Date(),
                                        new Date()));
            return new MessageResponse(msg.getHeader(), MessageDeliveryStatus.ACCEPTED);
        });
        val taskSpec = taskSpec();
        val executor = Executors.newCachedThreadPool();
        val te = new TaskEngine(tdb,
                                cdb,
                                new DefaultInstanceScheduler(instanceDB, tdb, cdb),
                                comm,
                                new DefaultControllerRetrySpecFactory(),
                                new RandomInstanceIdGenerator(),
                                Executors.defaultThreadFactory(),
                                executor,
                                new JobExecutor<>(executor),
                                new InMemoryClusterStateDB(),
                                le, ControllerTestUtils.DEFAULT_CLUSTER_OP);

        te.handleTaskOp(new TaskCreateOperation(taskSpec, ControllerTestUtils.DEFAULT_CLUSTER_OP));
        CommonTestUtils.waitUntil(() -> !te.activeTasks().isEmpty());
        assertFalse(te.activeTasks().isEmpty());
        val task = te.activeTasks().get(0);
        //This simulates task completion
        tdb.updateTask(task.getSourceAppName(),
                       task.getTaskId(),
                       new TaskInfo(task.getSourceAppName(),
                                    task.getTaskId(),
                                    task.getInstanceId(),
                                    task.getExecutorId(),
                                    task.getHostname(),
                                    task.getExecutable(),
                                    task.getResources(),
                                    task.getVolumes(),
                                    task.getLoggingSpec(),
                                    task.getEnv(),
                                    RUNNING,
                                    task.getMetadata(),
                                    null,
                                    "",
                                    task.getCreated(),
                                    oldDate));
        CommonTestUtils.waitUntil(() -> te.activeTasks().isEmpty());
        assertTrue(te.activeTasks().isEmpty());
    }

    @Test
    void testRunZombieKill() {
        val tdb = new InMemoryTaskDB();
        val instanceDB = new InMemoryApplicationInstanceInfoDB();
        val cdb = new InMemoryClusterResourcesDB();
        cdb.update(IntStream.rangeClosed(1, 5).mapToObj(ControllerTestUtils::generateExecutorNode).toList());
        val le = mock(LeadershipEnsurer.class);
        when(le.isLeader()).thenReturn(true);

        val comm = mock(ControllerCommunicator.class);

        val taskSpec = taskSpec();
        when(comm.send(any(StopTaskMessage.class))).thenAnswer(invocationOnMock -> {
            val msg = invocationOnMock.getArgument(0, StopTaskMessage.class);
            tdb.updateTask(taskSpec.getSourceAppName(),
                           taskSpec.getTaskId(),
                           new TaskInfo(taskSpec.getSourceAppName(),
                                        taskSpec.getTaskId(),
                                        "TI00001",
                                        "EXECUTOR_5",
                                        "localhost",
                                        taskSpec.getExecutable(),
                                        null,
                                        taskSpec.getVolumes(),
                                        taskSpec.getLogging(),
                                        taskSpec.getEnv(),
                                        STOPPED,
                                        Map.of(),
                                        new TaskResult(TaskResult.Status.SUCCESSFUL, 0),
                                        "",
                                        new Date(),
                                        new Date()));
            return new MessageResponse(msg.getHeader(), MessageDeliveryStatus.ACCEPTED);
        });
        tdb.updateTask(taskSpec.getSourceAppName(),
                       taskSpec.getTaskId(),
                       new TaskInfo(taskSpec.getSourceAppName(),
                                    taskSpec.getTaskId(),
                                    "TI00001",
                                    "EXECUTOR_5",
                                    "localhost",
                                    taskSpec.getExecutable(),
                                    null,
                                    taskSpec.getVolumes(),
                                    taskSpec.getLogging(),
                                    taskSpec.getEnv(),
                                    RUNNING,
                                    Map.of(),
                                    new TaskResult(TaskResult.Status.SUCCESSFUL, 0),
                                    "",
                                    new Date(),
                                    new Date()));
        val executor = Executors.newCachedThreadPool();
        val te = new TaskEngine(tdb,
                                cdb,
                                new DefaultInstanceScheduler(instanceDB, tdb, cdb),
                                comm,
                                new DefaultControllerRetrySpecFactory(),
                                new RandomInstanceIdGenerator(),
                                Executors.defaultThreadFactory(),
                                executor,
                                new JobExecutor<>(executor),
                                new InMemoryClusterStateDB(),
                                le, ControllerTestUtils.DEFAULT_CLUSTER_OP);
        te.handleZombieTask(taskSpec.getSourceAppName(), taskSpec.getTaskId());
        CommonTestUtils.waitUntil(() -> tdb.task(taskSpec.getSourceAppName(), taskSpec.getTaskId())
                .map(TaskInfo::getState)
                .filter(STOPPED::equals)
                .isPresent());
        assertTrue(tdb.task(taskSpec.getSourceAppName(), taskSpec.getTaskId())
                            .map(TaskInfo::getState)
                            .filter(STOPPED::equals)
                            .isPresent());
    }

    @Test
    void testRunRecovery() {
        val tdb = new InMemoryTaskDB();
        val instanceDB = new InMemoryApplicationInstanceInfoDB();
        val cdb = new InMemoryClusterResourcesDB();
        cdb.update(IntStream.rangeClosed(1, 5).mapToObj(ControllerTestUtils::generateExecutorNode).toList());
        val le = mock(LeadershipEnsurer.class);
        when(le.isLeader()).thenReturn(true);

        val comm = mock(ControllerCommunicator.class);
        val taskSpec = taskSpec();
        when(comm.send(any(StopTaskMessage.class))).thenAnswer(invocationOnMock -> {
            val msg = invocationOnMock.getArgument(0, StopTaskMessage.class);
            tdb.updateTask(taskSpec.getSourceAppName(),
                           taskSpec.getTaskId(),
                           new TaskInfo(taskSpec.getSourceAppName(),
                                        taskSpec.getTaskId(),
                                        "TI00001",
                                        "EXECUTOR_5",
                                        "localhost",
                                        taskSpec.getExecutable(),
                                        null,
                                        taskSpec.getVolumes(),
                                        taskSpec.getLogging(),
                                        taskSpec.getEnv(),
                                        STOPPED,
                                        Map.of(),
                                        null,
                                        "",
                                        new Date(),
                                        new Date()));
            return new MessageResponse(msg.getHeader(), MessageDeliveryStatus.ACCEPTED);
        });
        val executor = Executors.newCachedThreadPool();
        val te = new TaskEngine(tdb,
                                cdb,
                                new DefaultInstanceScheduler(instanceDB, tdb, cdb),
                                comm,
                                new DefaultControllerRetrySpecFactory(),
                                new RandomInstanceIdGenerator(),
                                Executors.defaultThreadFactory(),
                                executor,
                                new JobExecutor<>(executor),
                                new InMemoryClusterStateDB(),
                                le, ControllerTestUtils.DEFAULT_CLUSTER_OP);
        te.registerTaskRunner(taskSpec.getSourceAppName(), taskSpec.getTaskId());
        tdb.updateTask(taskSpec.getSourceAppName(),
                       taskSpec.getTaskId(),
                       new TaskInfo(taskSpec.getSourceAppName(),
                                    taskSpec.getTaskId(),
                                    "TI00001",
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
        CommonTestUtils.waitUntil(() -> !te.activeTasks().isEmpty());
        val task = te.activeTasks().get(0);
        te.handleTaskOp(new TaskKillOperation(task.getSourceAppName(), task.getTaskId(), ControllerTestUtils.DEFAULT_CLUSTER_OP));
        CommonTestUtils.waitUntil(() -> te.activeTasks().isEmpty());
        assertTrue(te.activeTasks().isEmpty());
    }

    @Test
    void testTaskStartFail() {
        val tdb = new InMemoryTaskDB();
        val instanceDB = new InMemoryApplicationInstanceInfoDB();
        val cdb = new InMemoryClusterResourcesDB();
        cdb.update(IntStream.rangeClosed(1, 5).mapToObj(ControllerTestUtils::generateExecutorNode).toList());
        val le = mock(LeadershipEnsurer.class);
        when(le.isLeader()).thenReturn(true);

        val comm = mock(ControllerCommunicator.class);
        val taskSpec = taskSpec();
        tdb.updateTask(taskSpec.getSourceAppName(),
                       taskSpec.getTaskId(),
                       new TaskInfo(taskSpec.getSourceAppName(),
                                    taskSpec.getTaskId(),
                                    "TI00001",
                                    "EXECUTOR_5",
                                    "localhost",
                                    taskSpec.getExecutable(),
                                    null,
                                    taskSpec.getVolumes(),
                                    taskSpec.getLogging(),
                                    taskSpec.getEnv(),
                                    STOPPED,
                                    Map.of(),
                                    new TaskResult(TaskResult.Status.SUCCESSFUL, 0),
                                    "",
                                    new Date(),
                                    new Date()));
        val executor = Executors.newCachedThreadPool();
        val te = new TaskEngine(tdb,
                                cdb,
                                new DefaultInstanceScheduler(instanceDB, tdb, cdb),
                                comm,
                                new DefaultControllerRetrySpecFactory(),
                                new RandomInstanceIdGenerator(),
                                Executors.defaultThreadFactory(),
                                executor,
                                new JobExecutor<>(executor),
                                new InMemoryClusterStateDB(),
                                le, ControllerTestUtils.DEFAULT_CLUSTER_OP);
        val r = te.handleTaskOp(new TaskCreateOperation(taskSpec, ControllerTestUtils.DEFAULT_CLUSTER_OP));
        assertEquals(ValidationStatus.FAILURE, r.getStatus());
        assertEquals("Task already exists for TEST_TASK_SPEC/TEST_TASK_SPEC00001 with taskID: TEST_TASK_SPEC-TEST_TASK_SPEC00001", r.getMessages().get(0));
    }

    @Test
    @SuppressWarnings("unchecked")
    void testTaskStartSchedFail() {
        val tdb = new InMemoryTaskDB();
        val instanceDB = new InMemoryApplicationInstanceInfoDB();
        val cdb = new InMemoryClusterResourcesDB();
        cdb.update(IntStream.rangeClosed(1, 5).mapToObj(ControllerTestUtils::generateExecutorNode).toList());
        val le = mock(LeadershipEnsurer.class);
        when(le.isLeader()).thenReturn(true);

        val comm = mock(ControllerCommunicator.class);
        val taskSpec = taskSpec();
        val executor = Executors.newCachedThreadPool();
        val jobSched = (JobExecutor<Boolean>)mock(JobExecutor.class);
        when(jobSched.schedule(any(JobTopology.class), any(), any())).thenReturn(null);
        val te = new TaskEngine(tdb,
                                cdb,
                                new DefaultInstanceScheduler(instanceDB, tdb, cdb),
                                comm,
                                new DefaultControllerRetrySpecFactory(),
                                new RandomInstanceIdGenerator(),
                                Executors.defaultThreadFactory(),
                                executor,
                                jobSched,
                                new InMemoryClusterStateDB(),
                                le, ControllerTestUtils.DEFAULT_CLUSTER_OP);
        val r = te.handleTaskOp(new TaskCreateOperation(taskSpec, ControllerTestUtils.DEFAULT_CLUSTER_OP));
        assertEquals(ValidationStatus.FAILURE, r.getStatus());
        assertEquals("Could not schedule job to start the task.", r.getMessages().get(0));
    }

    @Test
    void testTaskKillFail() {
        val tdb = new InMemoryTaskDB();
        val instanceDB = new InMemoryApplicationInstanceInfoDB();
        val cdb = new InMemoryClusterResourcesDB();
        cdb.update(IntStream.rangeClosed(1, 5).mapToObj(ControllerTestUtils::generateExecutorNode).toList());
        val le = mock(LeadershipEnsurer.class);
        when(le.isLeader()).thenReturn(true);

        val comm = mock(ControllerCommunicator.class);
        val taskSpec = taskSpec();
        val executor = Executors.newCachedThreadPool();
        val te = new TaskEngine(tdb,
                                cdb,
                                new DefaultInstanceScheduler(instanceDB, tdb, cdb),
                                comm,
                                new DefaultControllerRetrySpecFactory(),
                                new RandomInstanceIdGenerator(),
                                Executors.defaultThreadFactory(),
                                executor,
                                new JobExecutor<>(executor),
                                new InMemoryClusterStateDB(),
                                le, ControllerTestUtils.DEFAULT_CLUSTER_OP);
        val r = te.handleTaskOp(new TaskKillOperation(taskSpec.getSourceAppName(), taskSpec.getTaskId(), ControllerTestUtils.DEFAULT_CLUSTER_OP));
        assertEquals(ValidationStatus.FAILURE, r.getStatus());
        assertEquals("Either task does not exist or has already finished for TEST_TASK_SPEC/TEST_TASK_SPEC00001", r.getMessages().get(0));
    }
    @Test
    @SuppressWarnings("unchecked")
    void testTaskKillSchedFail() {
        val tdb = mock(TaskDB.class);
        val instanceDB = new InMemoryApplicationInstanceInfoDB();
        val cdb = new InMemoryClusterResourcesDB();
        cdb.update(IntStream.rangeClosed(1, 5).mapToObj(ControllerTestUtils::generateExecutorNode).toList());
        val le = mock(LeadershipEnsurer.class);
        when(le.isLeader()).thenReturn(true);

        val comm = mock(ControllerCommunicator.class);
        val taskSpec = taskSpec();
        val executor = Executors.newCachedThreadPool();
        val jobSched = (JobExecutor<Boolean>)mock(JobExecutor.class);
        when(jobSched.schedule(any(JobTopology.class), any(), any())).thenReturn(null);
        when(tdb.task(taskSpec.getSourceAppName(), taskSpec.getTaskId()))
                .thenReturn(Optional.of(
                        new TaskInfo(taskSpec.getSourceAppName(),
                                     taskSpec.getTaskId(),
                                     "TI00001",
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
                                     new Date())));
        when(tdb.onStateChange()).thenReturn(new ConsumingFireForgetSignal<>());
        val te = new TaskEngine(tdb,
                                cdb,
                                new DefaultInstanceScheduler(instanceDB, tdb, cdb),
                                comm,
                                new DefaultControllerRetrySpecFactory(),
                                new RandomInstanceIdGenerator(),
                                Executors.defaultThreadFactory(),
                                executor,
                                jobSched,
                                new InMemoryClusterStateDB(),
                                le, ControllerTestUtils.DEFAULT_CLUSTER_OP);
        te.registerTaskRunner(taskSpec.getSourceAppName(), taskSpec.getTaskId());

        val r = te.handleTaskOp(new TaskKillOperation(taskSpec.getSourceAppName(), taskSpec.getTaskId(), ControllerTestUtils.DEFAULT_CLUSTER_OP));
        assertEquals(ValidationStatus.FAILURE, r.getStatus());
        assertEquals("Could not schedule job to stop the task.", r.getMessages().get(0));
    }

    @Test
    void testResourceCrunch() {
        val tdb = new InMemoryTaskDB();
        val instanceDB = new InMemoryApplicationInstanceInfoDB();
        val cdb = new InMemoryClusterResourcesDB();
        cdb.update(List.of( new ExecutorNodeData("poor-host",
                                                 8080,
                                                 NodeTransportType.HTTP,
                                                 new Date(),
                                                 new ExecutorResourceSnapshot("PE1",
                                                                              new AvailableCPU(Map.of(),
                                                                                               Map.of()),
                                                                              new AvailableMemory(
                                                                                      Map.of(),
                                                                                      Map.of())),
                                                 List.of(),
                                                 List.of(),
                                                 Set.of(),
                                                 false)));
        val le = mock(LeadershipEnsurer.class);
        when(le.isLeader()).thenReturn(true);

        val comm = mock(ControllerCommunicator.class);
        val executor = Executors.newCachedThreadPool();
        val te = new TaskEngine(tdb,
                                cdb,
                                new DefaultInstanceScheduler(instanceDB, tdb, cdb),
                                comm,
                                new DefaultControllerRetrySpecFactory(),
                                new RandomInstanceIdGenerator(),
                                Executors.defaultThreadFactory(),
                                executor,
                                new JobExecutor<>(executor),
                                new InMemoryClusterStateDB(),
                                le, ControllerTestUtils.DEFAULT_CLUSTER_OP);
        val r = te.handleTaskOp(new TaskCreateOperation(ControllerTestUtils.taskSpec(), ControllerTestUtils.DEFAULT_CLUSTER_OP));
        assertEquals(ValidationStatus.FAILURE, r.getStatus());
        assertEquals(2, r.getMessages().size());
    }
}