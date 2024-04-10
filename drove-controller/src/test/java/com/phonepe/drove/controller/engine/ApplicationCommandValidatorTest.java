package com.phonepe.drove.controller.engine;

import com.phonepe.drove.controller.ControllerTestBase;
import com.phonepe.drove.controller.ControllerTestUtils;
import com.phonepe.drove.controller.config.ControllerOptions;
import com.phonepe.drove.controller.resourcemgmt.ClusterResourcesDB;
import com.phonepe.drove.controller.statedb.ApplicationInstanceInfoDB;
import com.phonepe.drove.controller.statedb.ApplicationStateDB;
import com.phonepe.drove.models.application.ApplicationInfo;
import com.phonepe.drove.models.application.ApplicationState;
import com.phonepe.drove.models.application.MountedVolume;
import com.phonepe.drove.models.application.checks.CheckSpec;
import com.phonepe.drove.models.application.checks.HTTPCheckModeSpec;
import com.phonepe.drove.models.application.exposure.ExposureMode;
import com.phonepe.drove.models.application.exposure.ExposureSpec;
import com.phonepe.drove.models.operation.ClusterOpSpec;
import com.phonepe.drove.models.operation.ops.*;
import io.dropwizard.util.Duration;
import lombok.val;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static com.phonepe.drove.models.common.HTTPVerb.GET;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

/**
 *
 */
class ApplicationCommandValidatorTest extends ControllerTestBase {


    private final ApplicationStateDB asDB = mock(ApplicationStateDB.class);
    private final ClusterResourcesDB crDB = mock(ClusterResourcesDB.class);
    private final ApplicationInstanceInfoDB aiDB = mock(ApplicationInstanceInfoDB.class);

    private final ApplicationEngine engine = mock(ApplicationEngine.class);

    @BeforeEach
    void resetMocks() {
        reset(asDB, crDB, aiDB, engine);
    }

    @Test
    void testNoState() {
        val validator = new ApplicationCommandValidator(asDB, crDB, aiDB, ControllerOptions.DEFAULT);
        val appId = "SomeAppId";
        when(engine.applicationState(appId)).thenReturn(Optional.empty());
        val res = validator.validate(engine,
                                     new ApplicationDestroyOperation(appId,
                                                                     ClusterOpSpec.DEFAULT));
        ensureFailure(res,
                      "No state found for app: SomeAppId"
                     );
    }

    @Test
    void testNoOpInInitState() {
        val validator = new ApplicationCommandValidator(asDB, crDB, aiDB, ControllerOptions.DEFAULT);
        val appId = "SomeAppId";
        when(engine.applicationState(appId)).thenReturn(Optional.of(ApplicationState.INIT));
        val res = validator.validate(engine,
                                     new ApplicationDestroyOperation(appId,
                                                                     ClusterOpSpec.DEFAULT));
        ensureFailure(res,
                      "No operations allowed for SomeAppId as it is in INIT state"
                     );
    }

    @Test
    void testInvalidOpsInMonitoringState() {
        testNoOpState(ApplicationState.MONITORING,
                      "Only [DESTROY, RECOVER, SCALE_INSTANCES, START_INSTANCES] allowed for app SomeAppId as it is " +
                              "in " +
                              "MONITORING state");
    }

    @Test
    void testInvalidOpsInRunningState() {
        val validator = new ApplicationCommandValidator(asDB, crDB, aiDB, ControllerOptions.DEFAULT);
        val appId = "SomeAppId";
        when(engine.applicationState(appId)).thenReturn(Optional.of(ApplicationState.RUNNING));
        when(asDB.application(appId)).thenReturn(Optional.of(new ApplicationInfo(appId,
                                                                                 ControllerTestUtils.appSpec(),
                                                                                 1,
                                                                                 null,
                                                                                 null)));
        ensureFailure(
                validator.validate(engine,
                                   new ApplicationDestroyOperation(appId, ClusterOpSpec.DEFAULT)),
                "Only [RECOVER, REPLACE_INSTANCES, SCALE_INSTANCES, START_INSTANCES, STOP_INSTANCES, SUSPEND] allowed" +
                        " for app SomeAppId as it is in RUNNING state");
    }

    @Test
    void testNoOpsInOutageDetectedState() {
        testNoOpState(ApplicationState.OUTAGE_DETECTED,
                      "No operations allowed for SomeAppId as it is in OUTAGE_DETECTED state");
    }

    @Test
    void testInvalidOpsInScalingState() {
        val validator = new ApplicationCommandValidator(asDB, crDB, aiDB, ControllerOptions.DEFAULT);
        val appId = "SomeAppId";
        when(engine.applicationState(appId)).thenReturn(Optional.of(ApplicationState.SCALING_REQUESTED));
        when(asDB.application(appId)).thenReturn(Optional.of(new ApplicationInfo(appId,
                                                                                 ControllerTestUtils.appSpec(),
                                                                                 1,
                                                                                 null,
                                                                                 null)));
        ensureFailure(
                validator.validate(engine,
                                   new ApplicationDestroyOperation(appId, ClusterOpSpec.DEFAULT)),
                "Only [SCALE_INSTANCES] allowed for app SomeAppId as it is in SCALING_REQUESTED state");
    }

    @Test
    void testNoOpsInStopRequestedState() {
        testNoOpState(ApplicationState.STOP_INSTANCES_REQUESTED,
                      "No operations allowed for SomeAppId as it is in STOP_INSTANCES_REQUESTED state");
    }

    @Test
    void testNoOpsInReplaceRequestedState() {
        testNoOpState(ApplicationState.REPLACE_INSTANCES_REQUESTED,
                      "No operations allowed for SomeAppId as it is in REPLACE_INSTANCES_REQUESTED state");
    }

    @Test
    void testNoOpsInDestroyRequestedState() {
        testNoOpState(ApplicationState.DESTROY_REQUESTED,
                      "No operations allowed for SomeAppId as it is in DESTROY_REQUESTED state");
    }

    @Test
    void testNoOpsInDestroyedState() {
        testNoOpState(ApplicationState.DESTROYED,
                      "No operations allowed for SomeAppId as it is in DESTROYED state");
    }


    @Test
    void testNoOpsInFailedState() {
        testNoOpState(ApplicationState.FAILED,
                      "No operations allowed for SomeAppId as it is in FAILED state");
    }


    @Test
    void testCreateSuccess() {
        val validator = new ApplicationCommandValidator(asDB, crDB, aiDB, ControllerOptions.DEFAULT);
        val res = validator.validate(engine,
                                     new ApplicationCreateOperation(ControllerTestUtils.appSpec(1),
                                                                    1,
                                                                    ClusterOpSpec.DEFAULT));
        ensureSuccess(res);
    }

    @Test
    void testCreateNoAppId() {
        val validator = new ApplicationCommandValidator(asDB, crDB, aiDB, ControllerOptions.DEFAULT);
        val res = validator.validate(engine,
                                     new ApplicationCreateOperation(
                                             ControllerTestUtils.appSpec(1)
                                                     .withName(null)
                                                     .withVersion(null),
                                             1,
                                             ClusterOpSpec.DEFAULT));
        ensureFailure(res, "no app id found in operation");
    }

    @Test
    void testCreateForExitingAppId() {
        val validator = new ApplicationCommandValidator(asDB, crDB, aiDB, ControllerOptions.DEFAULT);
        when(engine.exists(anyString())).thenReturn(true);
        val res = validator.validate(engine,
                                     new ApplicationCreateOperation(ControllerTestUtils.appSpec(1),
                                                                    1,
                                                                    ClusterOpSpec.DEFAULT));
        ensureFailure(res, "App TEST_SPEC-00001 already exists");
    }

    @Test
    void testCreateInvalidHealthCheckPortName() {
        val validator = new ApplicationCommandValidator(asDB, crDB, aiDB, ControllerOptions.DEFAULT);
        val spec = ControllerTestUtils.appSpec(1)
                .withHealthcheck(new CheckSpec(new HTTPCheckModeSpec(HTTPCheckModeSpec.Protocol.HTTP,
                                                                     "Invalid",
                                                                     "/",
                                                                     GET,
                                                                     Set.of(200),
                                                                     null,
                                                                     Duration.seconds(1)),
                                               Duration.seconds(1),
                                               Duration.seconds(1),
                                               3,
                                               Duration.seconds(1)));
        val res = validator.validate(engine,
                                     new ApplicationCreateOperation(spec,
                                                                    1,
                                                                    ClusterOpSpec.DEFAULT));
        ensureFailure(res, "Invalid port name for health check: Invalid. Available ports: [main]");
    }

    @Test
    void testCreateInvalidReadinessCheckPortName() {
        val validator = new ApplicationCommandValidator(asDB, crDB, aiDB, ControllerOptions.DEFAULT);
        val spec = ControllerTestUtils.appSpec(1)
                .withReadiness(new CheckSpec(new HTTPCheckModeSpec(HTTPCheckModeSpec.Protocol.HTTP,
                                                                   "Invalid",
                                                                   "/",
                                                                   GET,
                                                                   Set.of(200),
                                                                   null,
                                                                   Duration.seconds(1)),
                                             Duration.seconds(1),
                                             Duration.seconds(1),
                                             3,
                                             Duration.seconds(1)));
        val res = validator.validate(engine,
                                     new ApplicationCreateOperation(spec,
                                                                    1,
                                                                    ClusterOpSpec.DEFAULT));
        ensureFailure(res, "Invalid port name for health check: Invalid. Available ports: [main]");
    }

    @Test
    void testCreateMissingResourceRequirements() {
        val validator = new ApplicationCommandValidator(asDB, crDB, aiDB, ControllerOptions.DEFAULT);
        val spec = ControllerTestUtils.appSpec(1)
                .withResources(List.of());
        val res = validator.validate(engine,
                                     new ApplicationCreateOperation(spec,
                                                                    1,
                                                                    ClusterOpSpec.DEFAULT));
        ensureFailure(res,
                      "Cpu requirements are mandatory",
                      "Memory requirements are mandatory"
                     );
    }

    @Test
    void testCreateWrongPortInExposureSpec() {
        val validator = new ApplicationCommandValidator(asDB, crDB, aiDB, ControllerOptions.DEFAULT);
        val spec = ControllerTestUtils.appSpec(1)
                .withExposureSpec(new ExposureSpec("blah", "Invalid", ExposureMode.ALL));
        val res = validator.validate(engine,
                                     new ApplicationCreateOperation(spec,
                                                                    1,
                                                                    ClusterOpSpec.DEFAULT));
        ensureFailure(res,
                      "Exposed port name Invalid is undefined. Defined port names: [main]"
                     );
    }

    @Test
    void testCreateWhitelistedDirMounts() {
        val validator = new ApplicationCommandValidator(asDB, crDB, aiDB,
                                                        ControllerOptions.DEFAULT
                                                                .withAllowedMountDirs(List.of("/tmp")));
        {
            val spec = ControllerTestUtils.appSpec(1)
                    .withVolumes(List.of(new MountedVolume("/var/log",
                                                           "/var/log",
                                                           MountedVolume.MountMode.READ_WRITE)));
            val res = validator.validate(engine,
                                         new ApplicationCreateOperation(spec,
                                                                        1,
                                                                        ClusterOpSpec.DEFAULT));
            ensureFailure(res,
                          "Volume mount requested on non whitelisted host directory: /var/log"
                         );
        }
        {
            val spec = ControllerTestUtils.appSpec(1)
                    .withVolumes(List.of(new MountedVolume("/var/log",
                                                           "/tmp/log",
                                                           MountedVolume.MountMode.READ_WRITE)));
            val res = validator.validate(engine,
                                         new ApplicationCreateOperation(spec,
                                                                        1,
                                                                        ClusterOpSpec.DEFAULT));
            ensureSuccess(res);
        }
    }


    @Test
    void testStartInstSuccess() {
        val validator = new ApplicationCommandValidator(asDB, crDB, aiDB, ControllerOptions.DEFAULT);
        val appId = "SomeAppId";
        when(engine.applicationState(appId)).thenReturn(Optional.of(ApplicationState.MONITORING));
        when(asDB.application(appId)).thenReturn(Optional.of(new ApplicationInfo(appId,
                                                                                 ControllerTestUtils.appSpec(),
                                                                                 1,
                                                                                 null,
                                                                                 null)));
        when(aiDB.instanceCount(eq(appId), anySet())).thenReturn(2L);

        when(crDB.currentSnapshot(true))
                .thenReturn(List.of(ControllerTestUtils.executorHost(8000)));

        ensureSuccess(validator.validate(
                engine, new ApplicationStartInstancesOperation(appId, 1, ClusterOpSpec.DEFAULT)));
    }

    @Test
    void testStartInstNoResources() {
        val validator = new ApplicationCommandValidator(asDB, crDB, aiDB, ControllerOptions.DEFAULT);
        val appId = "SomeAppId";
        when(engine.applicationState(appId)).thenReturn(Optional.of(ApplicationState.MONITORING));
        when(asDB.application(appId)).thenReturn(Optional.of(new ApplicationInfo(appId,
                                                                                 ControllerTestUtils.appSpec(),
                                                                                 1,
                                                                                 null,
                                                                                 null)));

        when(crDB.currentSnapshot(true))
                .thenReturn(List.of(ControllerTestUtils.executorHost(8000)));

        ensureFailure(validator.validate(
                              engine, new ApplicationStartInstancesOperation(appId, 100, ClusterOpSpec.DEFAULT)),
                      "Cluster does not have enough CPU. Required: 100 Available: 3",
                      "Cluster does not have enough Memory. Required: 51200 Available: 8448");
    }

    @Test
    void testScaleSuccess() {
        val validator = new ApplicationCommandValidator(asDB, crDB, aiDB, ControllerOptions.DEFAULT);
        val appId = "SomeAppId";
        when(engine.applicationState(appId)).thenReturn(Optional.of(ApplicationState.MONITORING));
        when(asDB.application(appId)).thenReturn(Optional.of(new ApplicationInfo(appId,
                                                                                 ControllerTestUtils.appSpec(),
                                                                                 1,
                                                                                 null,
                                                                                 null)));
        when(aiDB.instanceCount(eq(appId), anySet())).thenReturn(2L);

        when(crDB.currentSnapshot(true))
                .thenReturn(List.of(ControllerTestUtils.executorHost(8000)));

        ensureSuccess(validator.validate(
                engine, new ApplicationScaleOperation(appId, 1, ClusterOpSpec.DEFAULT)));
    }

    @Test
    void testScaleInvalidId() {
        val validator = new ApplicationCommandValidator(asDB, crDB, aiDB, ControllerOptions.DEFAULT);
        val appId = "SomeAppId";
        when(engine.applicationState(anyString())).thenReturn(Optional.of(ApplicationState.MONITORING));
        when(asDB.application(appId)).thenReturn(Optional.empty());
        when(crDB.currentSnapshot(true))
                .thenReturn(List.of(ControllerTestUtils.executorHost(8000)));

        ensureFailure(validator.validate(
                              engine, new ApplicationScaleOperation(appId, 1, ClusterOpSpec.DEFAULT)),
                      "No spec found for app SomeAppId");
    }

    @Test
    void testScaleNoResource() {
        val validator = new ApplicationCommandValidator(asDB, crDB, aiDB, ControllerOptions.DEFAULT);
        val appId = "SomeAppId";
        when(engine.applicationState(anyString())).thenReturn(Optional.of(ApplicationState.MONITORING));
        when(asDB.application(appId)).thenReturn(Optional.of(new ApplicationInfo(appId,
                                                                                 ControllerTestUtils.appSpec(),
                                                                                 1,
                                                                                 null,
                                                                                 null)));
        when(crDB.currentSnapshot(true))
                .thenReturn(List.of(ControllerTestUtils.executorHost(8000)));

        ensureFailure(validator.validate(
                              engine, new ApplicationScaleOperation(appId, 100, ClusterOpSpec.DEFAULT)),
                      "Cluster does not have enough CPU. Required: 100 Available: 3",
                      "Cluster does not have enough Memory. Required: 51200 Available: 8448");
    }

    @Test
    void testStopInstSuccess() {
        val validator = new ApplicationCommandValidator(asDB, crDB, aiDB, ControllerOptions.DEFAULT);
        val appId = "SomeAppId";
        when(engine.applicationState(appId)).thenReturn(Optional.of(ApplicationState.RUNNING));
        val spec = ControllerTestUtils.appSpec();
        when(asDB.application(appId)).thenReturn(Optional.of(new ApplicationInfo(appId,
                                                                                 spec,
                                                                                 1,
                                                                                 null,
                                                                                 null)));
        when(aiDB.activeInstances(appId, 0, Integer.MAX_VALUE))
                .thenReturn(List.of(ControllerTestUtils.generateInstanceInfo(appId, spec, 1),
                                    ControllerTestUtils.generateInstanceInfo(appId, spec, 2)));
        when(crDB.currentSnapshot(true))
                .thenReturn(List.of(ControllerTestUtils.executorHost(8000)));

        ensureSuccess(validator.validate(
                engine,
                new ApplicationStopInstancesOperation(appId, List.of("TI-00001"), false, ClusterOpSpec.DEFAULT)));
    }

    @Test
    void testStopInstInvalidId() {
        val validator = new ApplicationCommandValidator(asDB, crDB, aiDB, ControllerOptions.DEFAULT);
        val appId = "SomeAppId";
        when(engine.applicationState(appId)).thenReturn(Optional.of(ApplicationState.RUNNING));
        val spec = ControllerTestUtils.appSpec();
        when(asDB.application(appId)).thenReturn(Optional.of(new ApplicationInfo(appId,
                                                                                 spec,
                                                                                 1,
                                                                                 null,
                                                                                 null)));
        when(aiDB.activeInstances(appId, 0, Integer.MAX_VALUE))
                .thenReturn(List.of(ControllerTestUtils.generateInstanceInfo(appId, spec, 1),
                                    ControllerTestUtils.generateInstanceInfo(appId, spec, 2)));
        when(crDB.currentSnapshot(true))
                .thenReturn(List.of(ControllerTestUtils.executorHost(8000)));

        ensureFailure(validator.validate(
                engine,
                new ApplicationStopInstancesOperation(appId, List.of("Invalid"), false, ClusterOpSpec.DEFAULT)),
                      "App SomeAppId does not have any instances with the following ids: Invalid");
    }

    @Test
    void testReplaceInstSuccess() {
        val validator = new ApplicationCommandValidator(asDB, crDB, aiDB, ControllerOptions.DEFAULT);
        val appId = "SomeAppId";
        when(engine.applicationState(appId)).thenReturn(Optional.of(ApplicationState.RUNNING));
        val spec = ControllerTestUtils.appSpec();
        when(asDB.application(appId)).thenReturn(Optional.of(new ApplicationInfo(appId,
                                                                                 spec,
                                                                                 1,
                                                                                 null,
                                                                                 null)));
        when(aiDB.instance(appId, "TI-00001"))
                .thenReturn(Optional.of(ControllerTestUtils.generateInstanceInfo(appId, spec, 1)));
        when(crDB.currentSnapshot(true))
                .thenReturn(List.of(ControllerTestUtils.executorHost(8000)));

        ensureSuccess(validator.validate(
                engine,
                new ApplicationReplaceInstancesOperation(appId, Set.of("TI-00001"), ClusterOpSpec.DEFAULT)));
    }

    @Test
    void testReplaceInstInvalidId() {
        val validator = new ApplicationCommandValidator(asDB, crDB, aiDB, ControllerOptions.DEFAULT);
        val appId = "SomeAppId";
        when(engine.applicationState(appId)).thenReturn(Optional.of(ApplicationState.RUNNING));
        val spec = ControllerTestUtils.appSpec();
        when(asDB.application(appId)).thenReturn(Optional.of(new ApplicationInfo(appId,
                                                                                 spec,
                                                                                 1,
                                                                                 null,
                                                                                 null)));
        when(crDB.currentSnapshot(true))
                .thenReturn(List.of(ControllerTestUtils.executorHost(8000)));

        ensureFailure(validator.validate(
                engine,
                new ApplicationReplaceInstancesOperation(appId, Set.of("Invalid"), ClusterOpSpec.DEFAULT)),
                      "There are no replaceable healthy instances with ids: [Invalid]");
    }
    
    @Test
    void testDestroyAppSuccess() {
        val validator = new ApplicationCommandValidator(asDB, crDB, aiDB, ControllerOptions.DEFAULT);
        val appId = "SomeAppId";
        when(engine.applicationState(appId)).thenReturn(Optional.of(ApplicationState.MONITORING));
        val spec = ControllerTestUtils.appSpec();
        when(asDB.application(appId)).thenReturn(Optional.of(new ApplicationInfo(appId,
                                                                                 spec,
                                                                                 1,
                                                                                 null,
                                                                                 null)));
        ensureSuccess(validator.validate(
                engine,
                new ApplicationDestroyOperation(appId, ClusterOpSpec.DEFAULT)));
    }

    @Test
    void testSuspendAppSuccess() {
        val validator = new ApplicationCommandValidator(asDB, crDB, aiDB, ControllerOptions.DEFAULT);
        val appId = "SomeAppId";
        when(engine.applicationState(appId)).thenReturn(Optional.of(ApplicationState.RUNNING));
        val spec = ControllerTestUtils.appSpec();
        when(asDB.application(appId)).thenReturn(Optional.of(new ApplicationInfo(appId,
                                                                                 spec,
                                                                                 1,
                                                                                 null,
                                                                                 null)));
        ensureSuccess(validator.validate(
                engine,
                new ApplicationSuspendOperation(appId, ClusterOpSpec.DEFAULT)));
    }

    @Test
    void testRecoverAppSuccess() {
        val validator = new ApplicationCommandValidator(asDB, crDB, aiDB, ControllerOptions.DEFAULT);
        val appId = "SomeAppId";
        when(engine.applicationState(appId)).thenReturn(Optional.of(ApplicationState.RUNNING));
        val spec = ControllerTestUtils.appSpec();
        when(asDB.application(appId)).thenReturn(Optional.of(new ApplicationInfo(appId,
                                                                                 spec,
                                                                                 1,
                                                                                 null,
                                                                                 null)));
        ensureSuccess(validator.validate(
                engine,
                new ApplicationRecoverOperation(appId)));
    }

    private void testNoOpState(ApplicationState state, String errorMessage) {
        val validator = new ApplicationCommandValidator(asDB, crDB, aiDB, ControllerOptions.DEFAULT);
        val appId = "SomeAppId";
        when(engine.applicationState(appId)).thenReturn(Optional.of(state));
        when(asDB.application(appId)).thenReturn(Optional.of(new ApplicationInfo(appId,
                                                                                 ControllerTestUtils.appSpec(),
                                                                                 1,
                                                                                 null,
                                                                                 null)));
        ensureFailure(
                validator.validate(engine,
                                   new ApplicationStopInstancesOperation(appId,
                                                                         List.of(),
                                                                         true,
                                                                         ClusterOpSpec.DEFAULT)),
                errorMessage);
    }

    private static void ensureSuccess(ValidationResult res) {
        assertEquals(ValidationStatus.SUCCESS, res.getStatus(),
                     () -> "Messages: " + res.getMessages());
        assertEquals(List.of("Success"),
                     res.getMessages(), () -> "Messages: " + res.getMessages());
    }

    private static void ensureFailure(ValidationResult res, String... expectedError) {
        assertEquals(ValidationStatus.FAILURE, res.getStatus());
//        assertEquals(expectedError.length, res.getMessages().size());
        assertEquals(Arrays.asList(expectedError),
                     res.getMessages(), () -> "Messages: " + res.getMessages());
    }


}