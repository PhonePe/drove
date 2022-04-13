package com.phonepe.drove.controller;

import com.phonepe.drove.common.CommonTestUtils;
import com.phonepe.drove.common.retry.CompositeRetrySpec;
import com.phonepe.drove.common.retry.MaxRetriesRetrySpec;
import com.phonepe.drove.common.retry.RetryOnAllExceptionsSpec;
import com.phonepe.drove.common.retry.RetrySpec;
import com.phonepe.drove.controller.resourcemgmt.AllocatedExecutorNode;
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
import com.phonepe.drove.models.info.nodedata.NodeTransportType;
import com.phonepe.drove.models.info.resources.allocation.CPUAllocation;
import com.phonepe.drove.models.info.resources.allocation.MemoryAllocation;
import io.dropwizard.util.Duration;
import lombok.experimental.UtilityClass;

import java.util.Collections;
import java.util.List;

/**
 *
 */
@UtilityClass
public class ControllerTestUtils {
    public static final RetrySpec NO_RETRY_SPEC = new CompositeRetrySpec(List.of(new MaxRetriesRetrySpec(1),
                                                                                 new RetryOnAllExceptionsSpec()));

    public static ApplicationSpec appSpec() {
        return new ApplicationSpec("T001",
                                   "TEST_SPEC",
                                   new DockerCoordinates(CommonTestUtils.IMAGE_NAME, Duration.seconds(100)),
                                   Collections.singletonList(new PortSpec("main", 8000, PortType.HTTP)),
                                   List.of(new MountedVolume("/tmp", "/tmp", MountedVolume.MountMode.READ_ONLY)),
                                   JobType.SERVICE,
                                   LocalLoggingSpec.DEFAULT,
                                   null,
                                   List.of(new CPURequirement(1), new MemoryRequirement(512)),
                                   new AnyPlacementPolicy(),
                                   new CheckSpec(new HTTPCheckModeSpec("http",
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
                                   new CheckSpec(new HTTPCheckModeSpec("http",
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
        return new AllocatedExecutorNode("Ex1",
                                         "localhost",
                                         port,
                                         NodeTransportType.HTTP,
                                         new CPUAllocation(Collections.singletonMap(0, Collections.singleton(2))),
                                         new MemoryAllocation(Collections.singletonMap(0, 512L)),
                                         Collections.emptySet());
    }

}
