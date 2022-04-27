package com.phonepe.drove.controller;

import com.phonepe.drove.common.CommonTestUtils;
import com.phonepe.drove.common.retry.*;
import com.phonepe.drove.controller.resourcemgmt.AllocatedExecutorNode;
import com.phonepe.drove.controller.resourcemgmt.ExecutorHostInfo;
import com.phonepe.drove.models.application.*;
import com.phonepe.drove.models.application.checks.CheckSpec;
import com.phonepe.drove.models.application.checks.HTTPCheckModeSpec;
import com.phonepe.drove.models.application.executable.DockerCoordinates;
import com.phonepe.drove.models.application.exposure.ExposureMode;
import com.phonepe.drove.models.application.exposure.ExposureSpec;
import com.phonepe.drove.models.application.logging.LocalLoggingSpec;
import com.phonepe.drove.models.application.placement.policies.AnyPlacementPolicy;
import com.phonepe.drove.models.application.requirements.CPURequirement;
import com.phonepe.drove.models.application.requirements.MemoryRequirement;
import com.phonepe.drove.models.common.HTTPVerb;
import com.phonepe.drove.models.info.ExecutorResourceSnapshot;
import com.phonepe.drove.models.info.nodedata.ExecutorNodeData;
import com.phonepe.drove.models.info.nodedata.NodeTransportType;
import com.phonepe.drove.models.info.resources.allocation.CPUAllocation;
import com.phonepe.drove.models.info.resources.allocation.MemoryAllocation;
import com.phonepe.drove.models.info.resources.available.AvailableCPU;
import com.phonepe.drove.models.info.resources.available.AvailableMemory;
import io.dropwizard.util.Duration;
import lombok.experimental.UtilityClass;
import lombok.val;

import java.util.*;

/**
 *
 */
@UtilityClass
public class ControllerTestUtils {

    public static final String EXECUTOR_ID = "Ex1";
    public static final RetrySpec NO_RETRY_SPEC = new CompositeRetrySpec(List.of(new MaxRetriesRetrySpec(1),
                                                                                 new RetryOnAllExceptionsSpec(),
                                                                                 new MaxDurationRetrySpec(java.time.Duration.ofMillis(100))));

    public static ApplicationSpec appSpec() {
        return appSpec(1);
    }

    public static ApplicationSpec appSpec(int version) {
        return new ApplicationSpec("TEST_SPEC",
                                   String.format("%05d", version),
                                   new DockerCoordinates(CommonTestUtils.IMAGE_NAME, Duration.seconds(100)),
                                   Collections.singletonList(new PortSpec("main", 8000, PortType.HTTP)),
                                   List.of(new MountedVolume("/tmp", "/tmp", MountedVolume.MountMode.READ_ONLY)),
                                   JobType.SERVICE,
                                   LocalLoggingSpec.DEFAULT,
                                   List.of(new CPURequirement(1), new MemoryRequirement(512)),
                                   new AnyPlacementPolicy(),
                                   new CheckSpec(new HTTPCheckModeSpec(HTTPCheckModeSpec.Protocol.HTTP,
                                                                       "main",
                                                                       "/",
                                                                       HTTPVerb.GET,
                                                                       Collections.singleton(200),
                                                                       "",
                                                                       Duration.seconds(1)),
                                                 Duration.seconds(1),
                                                 Duration.seconds(3),
                                                 3,
                                                 Duration.seconds(0)),
                                   new CheckSpec(new HTTPCheckModeSpec(HTTPCheckModeSpec.Protocol.HTTP,
                                                                       "main",
                                                                       "/",
                                                                       HTTPVerb.GET,
                                                                       Collections.singleton(200),
                                                                       "",
                                                                       Duration.seconds(1)),
                                                 Duration.seconds(1),
                                                 Duration.seconds(3),
                                                 3,
                                                 Duration.seconds(1)),
                                   Collections.emptyMap(),
                                   Collections.emptyMap(),
                                   new ExposureSpec("test.appform.io", "main", ExposureMode.ALL),
                                   null);
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

    public static ExecutorHostInfo executorHost(int port) {
        return new ExecutorHostInfo(
                "Ex1",
                new ExecutorNodeData(EXECUTOR_ID,
                                     port,
                                     NodeTransportType.HTTP,
                                     new Date(),
                                     new ExecutorResourceSnapshot(EXECUTOR_ID,
                                                                  new AvailableCPU(Map.of(0, Set.of(2, 3, 4)),
                                                                                   Map.of(1, Set.of(0, 1))),
                                                                  new AvailableMemory(
                                                                          Map.of(0, 3 * 128 * (2L ^ 20)),
                                                                          Map.of(0, 128 * (2L ^ 20)))),
                                     List.of(),
                                     Set.of(),
                                     false),
                Map.of(0, new ExecutorHostInfo.NumaNodeInfo()));
    }

    public static ExecutorNodeData generateExecutorNode(int index) {
        return generateExecutorNode(index, Set.of());
    }
    public static ExecutorNodeData generateExecutorNode(int index, Set<String> tags) {
        val executorId = executorId(index);
        return new ExecutorNodeData(String.format("host%05d", index),
                                    8080,
                                    NodeTransportType.HTTP,
                                    new Date(),
                                    new ExecutorResourceSnapshot(executorId,
                                                                 new AvailableCPU(Map.of(0, Set.of(0, 1, 2, 3, 4)),
                                                                                  Map.of(0, Set.of())),
                                                                 new AvailableMemory(
                                                                         Map.of(0, 5 * 512L ),
                                                                         Map.of(0, 0L))),
                                    List.of(),
                                    tags,
                                    false);
    }

    public static String executorId(int index) {
        return "EXECUTOR_" + index;
    }
}
