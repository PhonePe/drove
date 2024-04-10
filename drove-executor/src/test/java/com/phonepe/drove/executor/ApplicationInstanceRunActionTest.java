package com.phonepe.drove.executor;

import com.github.dockerjava.api.async.ResultCallbackTemplate;
import com.github.dockerjava.api.model.Frame;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.phonepe.drove.common.AbstractTestBase;
import com.phonepe.drove.common.CommonUtils;
import com.phonepe.drove.common.model.ApplicationInstanceSpec;
import com.phonepe.drove.executor.model.ExecutorInstanceInfo;
import com.phonepe.drove.executor.resourcemgmt.ResourceConfig;
import com.phonepe.drove.executor.statemachine.InstanceActionContext;
import com.phonepe.drove.executor.statemachine.application.actions.ApplicationExecutableFetchAction;
import com.phonepe.drove.executor.statemachine.application.actions.ApplicationInstanceRunAction;
import com.phonepe.drove.models.application.PortSpec;
import com.phonepe.drove.models.application.PortType;
import com.phonepe.drove.models.application.executable.DockerCoordinates;
import com.phonepe.drove.models.application.logging.LocalLoggingSpec;
import com.phonepe.drove.models.info.resources.allocation.CPUAllocation;
import com.phonepe.drove.models.info.resources.allocation.MemoryAllocation;
import com.phonepe.drove.models.instance.InstanceState;
import com.phonepe.drove.models.instance.LocalInstanceInfo;
import com.phonepe.drove.statemachine.StateData;
import io.dropwizard.util.Duration;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.Date;
import java.util.Map;
import java.util.UUID;

import static com.phonepe.drove.executor.ExecutorTestingUtils.DOCKER_CLIENT;
import static com.phonepe.drove.models.instance.InstanceState.PROVISIONING;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 *
 */
@Slf4j
class ApplicationInstanceRunActionTest extends AbstractTestBase {

    private static final class CmdOutputHandler extends ResultCallbackTemplate<CmdOutputHandler, Frame> {
        private final StringBuffer buffer = new StringBuffer();

        @Override
        public void onNext(Frame frame) {
            switch (frame.getStreamType()) {
                case STDOUT, STDERR, RAW -> buffer.append(new String(frame.getPayload()));
                default -> log.error("Unexpected stream type value: {}", frame.getStreamType());
            }
        }

        public String result() {
            return buffer.toString();
        }
    }

    @Test
    @SneakyThrows
    void testRun() {
        val appId = "T001";
        val instanceId = UUID.randomUUID().toString();
        val instanceSpec = new ApplicationInstanceSpec(appId,
                                                       "TEST_APP",
                                                       instanceId,
                                                       new DockerCoordinates(
                                                    "docker.io/santanusinha/test-service:0.1",
                                                    Duration.seconds(100)),
                                                       ImmutableList.of(new CPUAllocation(Collections.singletonMap(0,
                                                                                                        Collections.singleton(
                                                                                                                1))),
                                                             new MemoryAllocation(Collections.singletonMap(0, 512L))),
                                                       Collections.singletonList(new PortSpec("main", 3000, PortType.HTTP)),
                                                       Collections.emptyList(),
                                                       null,
                                                       null,
                                                       LocalLoggingSpec.DEFAULT,
                                                       Map.of("TEST_PREDEF_VAL", "PreDevValue",
                                                              "TEST_ENV_READ", "",
                                                              "TEST_ENV_READ_OVERRIDE", "OverriddenValue",
                                                              "TEST_ENV_UNDEFINED", ""
                                                              ),
                                                       null,
                                                       "TestToken");
        val executorId = CommonUtils.executorId(3000, "test-host");
        val ctx = new InstanceActionContext<>(executorId, instanceSpec, DOCKER_CLIENT, false);
        new ApplicationExecutableFetchAction(null).execute(ctx, StateData.create(InstanceState.PENDING, null));
        val newState
                = new ApplicationInstanceRunAction(new ResourceConfig(), ExecutorOptions.DEFAULT)
                .execute(ctx,
                         StateData.create(PROVISIONING,
                                          new ExecutorInstanceInfo(instanceSpec.getAppId(),
                                                                   instanceSpec.getAppName(),
                                                                   instanceSpec.getInstanceId(),
                                                                   executorId,
                                                                   new LocalInstanceInfo(CommonUtils.hostname(),
                                                                                         Collections.emptyMap()),
                                                                   instanceSpec.getResources(),
                                                                   Collections.emptyMap(),
                                                                   new Date(),
                                                                   new Date()),
                                          ""));
        assertEquals(InstanceState.UNREADY, newState.getState());
        assertEquals("PreDevValue", runCmd(ctx, "echo -n ${TEST_PREDEF_VAL}"));
        assertEquals("TestValue", runCmd(ctx, "echo -n ${TEST_ENV_READ}"));
        assertTrue(Strings.isNullOrEmpty(runCmd(ctx, "echo -n ${TEST_ENV_READ_INVALID}")));
        assertTrue(Strings.isNullOrEmpty(runCmd(ctx, "echo -n ${TEST_ENV_UNDEFINED}")));
        assertEquals("OverriddenValue", runCmd(ctx, "echo -n ${TEST_ENV_READ_OVERRIDE}"));
        DOCKER_CLIENT.stopContainerCmd(ctx.getDockerInstanceId()).exec();
    }

    @SneakyThrows
    private static String runCmd(InstanceActionContext<ApplicationInstanceSpec> ctx, String cmd) {
        val execId = DOCKER_CLIENT.execCreateCmd(ctx.getDockerInstanceId())
                .withAttachStderr(true)
                .withAttachStdout(true)
                .withCmd("sh", "-c", cmd)
                .exec()
                .getId();
        val callback =
                DOCKER_CLIENT.execStartCmd(execId)
                        .exec(new CmdOutputHandler())
                        .awaitCompletion();
        return callback.result();
    }
}