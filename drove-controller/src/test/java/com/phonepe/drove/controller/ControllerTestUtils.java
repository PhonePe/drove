package com.phonepe.drove.controller;

import com.phonepe.drove.common.CommonTestUtils;
import com.phonepe.drove.common.retry.*;
import com.phonepe.drove.controller.resourcemgmt.AllocatedExecutorNode;
import com.phonepe.drove.controller.resourcemgmt.ExecutorHostInfo;
import com.phonepe.drove.models.application.*;
import com.phonepe.drove.models.application.checks.CheckSpec;
import com.phonepe.drove.models.application.checks.HTTPCheckModeSpec;
import com.phonepe.drove.models.common.Protocol;
import com.phonepe.drove.models.application.executable.DockerCoordinates;
import com.phonepe.drove.models.application.exposure.ExposureMode;
import com.phonepe.drove.models.application.exposure.ExposureSpec;
import com.phonepe.drove.models.application.logging.LocalLoggingSpec;
import com.phonepe.drove.models.application.placement.policies.AnyPlacementPolicy;
import com.phonepe.drove.models.application.requirements.CPURequirement;
import com.phonepe.drove.models.application.requirements.MemoryRequirement;
import com.phonepe.drove.models.common.HTTPVerb;
import com.phonepe.drove.models.config.impl.InlineConfigSpec;
import com.phonepe.drove.models.info.ExecutorResourceSnapshot;
import com.phonepe.drove.models.info.nodedata.ExecutorNodeData;
import com.phonepe.drove.models.info.nodedata.NodeTransportType;
import com.phonepe.drove.models.info.resources.allocation.CPUAllocation;
import com.phonepe.drove.models.info.resources.allocation.MemoryAllocation;
import com.phonepe.drove.models.info.resources.available.AvailableCPU;
import com.phonepe.drove.models.info.resources.available.AvailableMemory;
import com.phonepe.drove.models.instance.InstanceInfo;
import com.phonepe.drove.models.instance.InstancePort;
import com.phonepe.drove.models.instance.InstanceState;
import com.phonepe.drove.models.instance.LocalInstanceInfo;
import com.phonepe.drove.models.operation.ClusterOpSpec;
import com.phonepe.drove.models.operation.deploy.FailureStrategy;
import com.phonepe.drove.models.task.TaskSpec;
import com.phonepe.drove.models.taskinstance.TaskInfo;
import com.phonepe.drove.models.taskinstance.TaskResult;
import com.phonepe.drove.models.taskinstance.TaskState;
import io.dropwizard.util.Duration;
import lombok.experimental.UtilityClass;
import lombok.val;

import java.util.*;

import static com.phonepe.drove.common.CommonTestUtils.base64;
import static com.phonepe.drove.models.instance.InstanceState.HEALTHY;

/**
 *
 */
@UtilityClass
public class ControllerTestUtils {

    public static final String EXECUTOR_ID = "Ex1";
    public static final RetrySpec NO_RETRY_SPEC = new CompositeRetrySpec(List.of(new MaxRetriesRetrySpec(1),
                                                                                 new RetryOnAllExceptionsSpec(),
                                                                                 new MaxDurationRetrySpec(java.time.Duration.ofMillis(
                                                                                         100))));
    public static final ClusterOpSpec DEFAULT_CLUSTER_OP = new ClusterOpSpec(ClusterOpSpec.DEFAULT_CLUSTER_OP_TIMEOUT,
                                                                             ClusterOpSpec.DEFAULT_CLUSTER_OP_PARALLELISM,
                                                                             FailureStrategy.STOP);

    public static ApplicationSpec appSpec() {
        return appSpec(1);
    }

    public static ApplicationSpec appSpec(int version) {
        return appSpec("TEST_SPEC", version);
    }

    public static ApplicationSpec appSpec(final String name, final int version) {
        return new ApplicationSpec(name,
                                   String.format("%05d", version),
                                   new DockerCoordinates(CommonTestUtils.APP_IMAGE_NAME, Duration.seconds(100)),
                                   Collections.singletonList(new PortSpec("main", 8000, PortType.HTTP)),
                                   List.of(new MountedVolume("/tmp", "/tmp", MountedVolume.MountMode.READ_ONLY)),
                                   List.of(new InlineConfigSpec("/files/drove.txt", base64("Drove Test"))),
                                   JobType.SERVICE,
                                   LocalLoggingSpec.DEFAULT,
                                   List.of(new CPURequirement(1), new MemoryRequirement(512)),
                                   new AnyPlacementPolicy(),
                                   new CheckSpec(new HTTPCheckModeSpec(Protocol.HTTP,
                                                                       "main",
                                                                       "/",
                                                                       HTTPVerb.GET,
                                                                       Collections.singleton(200),
                                                                       "",
                                                                       Duration.seconds(1),
                                                                       false),
                                                 Duration.seconds(1),
                                                 Duration.seconds(3),
                                                 3,
                                                 Duration.seconds(0)),
                                   new CheckSpec(new HTTPCheckModeSpec(Protocol.HTTP,
                                                                       "main",
                                                                       "/",
                                                                       HTTPVerb.GET,
                                                                       Collections.singleton(200),
                                                                       "",
                                                                       Duration.seconds(1),
                                                                       false),
                                                 Duration.seconds(1),
                                                 Duration.seconds(3),
                                                 3,
                                                 Duration.seconds(1)),
                                   Collections.emptyMap(),
                                   Collections.emptyMap(),
                                   new ExposureSpec("test.appform.io", "main", ExposureMode.ALL),
                                   null);
    }

    public static TaskSpec taskSpec() {
        return taskSpec(1);
    }

    public static TaskSpec taskSpec(int version) {
        return taskSpec("TEST_TASK_SPEC", version);
    }

    public static TaskSpec taskSpec(final String name, final int version) {
        return taskSpec(name, String.format("%s%05d", name, version));
    }

    public static TaskSpec taskSpec(final String name, String taskId) {
        return new TaskSpec(name,
                            taskId,
                            new DockerCoordinates(CommonTestUtils.TASK_IMAGE_NAME, Duration.seconds(100)),
                            List.of(new MountedVolume("/tmp", "/tmp", MountedVolume.MountMode.READ_ONLY)),
                            List.of(new InlineConfigSpec("/files/drove.txt", base64("Drove Test"))),
                            LocalLoggingSpec.DEFAULT,
                            List.of(new CPURequirement(1), new MemoryRequirement(512)),
                            new AnyPlacementPolicy(),
                            Collections.emptyMap(),
                            Collections.emptyMap());
    }

    public static AllocatedExecutorNode allocatedExecutorNode(int port) {
        return new AllocatedExecutorNode(EXECUTOR_ID,
                                         "localhost",
                                         port,
                                         NodeTransportType.HTTP,
                                         new CPUAllocation(Collections.singletonMap(0, Collections.singleton(2))),
                                         new MemoryAllocation(Collections.singletonMap(0, 512L)),
                                         Collections.emptySet());
    }

    public static ExecutorHostInfo executorHost(final int port) {
        return executorHost(port, List.of(), List.of());
    }

    public static ExecutorHostInfo executorHost(
            final int port,
            final List<InstanceInfo> appInstances,
            final List<TaskInfo> taskInstances) {
        return executorHost(1, port, appInstances, taskInstances);
    }

    public static ExecutorHostInfo executorHost(
            final int index,
            final int port,
            final List<InstanceInfo> appInstances,
            final List<TaskInfo> taskInstances) {
        return executorHost(index, port, appInstances, taskInstances, false);
    }

    public static ExecutorHostInfo executorHost(
            final int index,
            final int port,
            final List<InstanceInfo> appInstances,
            final List<TaskInfo> taskInstances,
            boolean blacklisted) {
        return executorHost("Ex" + index, index, port, appInstances, taskInstances, blacklisted);
    }

    public static ExecutorHostInfo executorHost(
            final String hostname,
            final int index,
            final int port,
            final List<InstanceInfo> appInstances,
            final List<TaskInfo> taskInstances,
            boolean blacklisted) {
        return new ExecutorHostInfo(
                "Ex" + index,
                new ExecutorNodeData(hostname,
                                     port,
                                     NodeTransportType.HTTP,
                                     new Date(),
                                     new ExecutorResourceSnapshot(EXECUTOR_ID,
                                                                  new AvailableCPU(Map.of(0, Set.of(2, 3, 4)),
                                                                                   Map.of(1, Set.of(0, 1))),
                                                                  new AvailableMemory(
                                                                          Map.of(0, 3 * 128 * (2L ^ 20)),
                                                                          Map.of(0, 128 * (2L ^ 20)))),
                                     appInstances,
                                     taskInstances, Set.of(),
                                     blacklisted),
                Map.of(0, new ExecutorHostInfo.NumaNodeInfo()));
    }


    public static ExecutorNodeData generateExecutorNode(int index) {
        return generateExecutorNode(index, Set.of());
    }

    public static ExecutorNodeData generateExecutorNode(int index, Set<String> tags) {
        return generateExecutorNode(index, tags, false);
    }

    public static ExecutorNodeData generateExecutorNode(int index, Set<String> tags, boolean blacklisted) {
        val executorId = executorId(index);
        return new ExecutorNodeData(String.format("host%05d", index),
                                    8080,
                                    NodeTransportType.HTTP,
                                    new Date(),
                                    new ExecutorResourceSnapshot(executorId,
                                                                 new AvailableCPU(Map.of(0, Set.of(0, 1, 2, 3, 4)),
                                                                                  Map.of(0, Set.of())),
                                                                 new AvailableMemory(
                                                                         Map.of(0, 5 * 512L),
                                                                         Map.of(0, 0L))),
                                    List.of(),
                                    List.of(),
                                    tags,
                                    blacklisted);
    }

    public static String executorId(int index) {
        return "EXECUTOR_" + index;
    }

    public static InstanceInfo generateInstanceInfo(final String appId, final ApplicationSpec spec, int idx) {
        return generateInstanceInfo(appId, spec, idx, HEALTHY);
    }

    public static InstanceInfo generateInstanceInfo(
            final String appId,
            final ApplicationSpec spec,
            int idx,
            InstanceState state) {
        return generateInstanceInfo(appId, spec, idx, state, new Date(), null);
    }

    public static InstanceInfo generateInstanceInfo(
            final String appId,
            final ApplicationSpec spec,
            int idx,
            InstanceState state,
            Date date,
            String errorMessage) {
        return new InstanceInfo(appId,
                                spec.getName(),
                                String.format("AI-%05d", idx),
                                EXECUTOR_ID,
                                new LocalInstanceInfo("localhost",
                                                      Collections.singletonMap("main",
                                                                               new InstancePort(
                                                                                       8000,
                                                                                       32000,
                                                                                       PortType.HTTP))),
                                List.of(new CPUAllocation(Map.of(0, Set.of(idx))),
                                        new MemoryAllocation(Map.of(0, 512L))),
                                state,
                                Collections.emptyMap(),
                                errorMessage,
                                date,
                                date);
    }

    public static TaskInfo generateTaskInfo(final TaskSpec spec, int idx) {
        return generateTaskInfo(spec, idx, TaskState.RUNNING);
    }

    public static TaskInfo generateTaskInfo(final TaskSpec spec, int idx, TaskState state) {
        return generateTaskInfo(spec, idx, state, new Date(), null);
    }

    public static TaskInfo generateTaskInfo(
            final TaskSpec spec,
            int idx,
            TaskState state,
            Date date,
            String errorMessage) {
        return generateTaskInfo(spec, idx, state, date, errorMessage, true);
    }

    public static TaskInfo generateTaskInfo(
            final TaskSpec spec,
            int idx,
            TaskState state,
            Date date,
            String errorMessage,
            boolean hasResult) {
        return new TaskInfo(spec.getSourceAppName(),
                            spec.getTaskId(),
                            String.format("TI-%s", idx),
                            EXECUTOR_ID,
                            "localhost",
                            spec.getExecutable(),
                            List.of(new CPUAllocation(Map.of(0, Set.of(idx))),
                                    new MemoryAllocation(Map.of(0, 512L))),
                            spec.getVolumes(),
                            spec.getLogging(),
                            spec.getEnv(),
                            state,
                            Collections.emptyMap(),
                            hasResult ? new TaskResult(TaskResult.Status.SUCCESSFUL, 0) : null,
                            errorMessage,
                            date,
                            date);
    }
}
