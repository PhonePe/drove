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

package com.phonepe.drove.controller.engine;

import com.phonepe.drove.controller.ControllerTestBase;
import com.phonepe.drove.controller.ControllerTestUtils;
import com.phonepe.drove.controller.config.ControllerOptions;
import com.phonepe.drove.controller.resourcemgmt.ClusterResourcesDB;
import com.phonepe.drove.controller.resourcemgmt.InstanceScheduler;
import com.phonepe.drove.controller.resourcemgmt.PlacementCheckResult;
import com.phonepe.drove.controller.securityprofile.SecurityPolicyEnforcer;
import com.phonepe.drove.controller.statedb.ApplicationInstanceInfoDB;
import com.phonepe.drove.controller.statedb.ApplicationStateDB;
import com.phonepe.drove.models.application.ApplicationInfo;
import com.phonepe.drove.models.application.ApplicationState;
import com.phonepe.drove.models.application.MountedVolume;
import com.phonepe.drove.models.application.checks.CheckSpec;
import com.phonepe.drove.models.application.checks.HTTPCheckModeSpec;
import com.phonepe.drove.models.application.devices.DirectDeviceSpec;
import com.phonepe.drove.models.application.exposure.ExposureMode;
import com.phonepe.drove.models.application.exposure.ExposureSpec;
import com.phonepe.drove.models.application.requirements.CPURequirement;
import com.phonepe.drove.models.application.requirements.GPURequirement;
import com.phonepe.drove.models.application.requirements.GpuType;
import com.phonepe.drove.models.application.requirements.GpuVendor;
import com.phonepe.drove.models.application.requirements.MemoryRequirement;
import com.phonepe.drove.models.common.Protocol;
import com.phonepe.drove.models.operation.ClusterOpSpec;
import com.phonepe.drove.models.operation.ops.ApplicationCreateOperation;
import com.phonepe.drove.models.operation.ops.ApplicationDestroyOperation;
import com.phonepe.drove.models.operation.ops.ApplicationRecoverOperation;
import com.phonepe.drove.models.operation.ops.ApplicationReplaceInstancesOperation;
import com.phonepe.drove.models.operation.ops.ApplicationScaleOperation;
import com.phonepe.drove.models.operation.ops.ApplicationStartInstancesOperation;
import com.phonepe.drove.models.operation.ops.ApplicationStopInstancesOperation;
import com.phonepe.drove.models.operation.ops.ApplicationSuspendOperation;
import io.dropwizard.util.Duration;
import lombok.val;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import com.phonepe.drove.models.application.placement.policies.RuleBasedPlacementPolicy;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static com.phonepe.drove.models.common.HTTPVerb.GET;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;
import com.phonepe.drove.controller.securityprofile.ContainerSecurityPolicyConfig;
import com.phonepe.drove.models.policies.ContainerSecurityProfile;
/**
 *
 */
class ApplicationCommandValidatorTest extends ControllerTestBase {

    private final ApplicationStateDB asDB = mock(ApplicationStateDB.class);
    private final ClusterResourcesDB crDB = mock(ClusterResourcesDB.class);
    private final ApplicationInstanceInfoDB aiDB = mock(ApplicationInstanceInfoDB.class);
    private final InstanceScheduler instanceScheduler = mock(InstanceScheduler.class);

    private final ApplicationLifecycleManagementEngine engine = mock(ApplicationLifecycleManagementEngine.class);

    @BeforeEach
    void resetMocks() {
        reset(asDB, crDB, aiDB, engine);
    }

    @Test
    void testNoState() {
        val validator = createValidator();
        val appId = "SomeAppId";
        when(engine.currentState(appId)).thenReturn(Optional.empty());
        val res = validator.validateOperation(engine,
                                              new ApplicationDestroyOperation(appId,
                                                                     ClusterOpSpec.DEFAULT));
        ensureFailure(res,
                      "No state found for app: SomeAppId"
                     );
    }

    @Test
    void testNoOpInInitState() {
        val validator = createValidator();
        val appId = "SomeAppId";
        when(engine.currentState(appId)).thenReturn(Optional.of(ApplicationState.INIT));
        val res = validator.validateOperation(engine,
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
        val validator = createValidator();
        val appId = "SomeAppId";
        when(engine.currentState(appId)).thenReturn(Optional.of(ApplicationState.RUNNING));
        when(asDB.application(appId)).thenReturn(Optional.of(new ApplicationInfo(appId,
                                                                                 ControllerTestUtils.appSpec(),
                                                                                 1,
                                                                                 null,
                                                                                 null)));
        ensureFailure(
                validator.validateOperation(engine,
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
        val validator = createValidator();
        val appId = "SomeAppId";
        when(engine.currentState(appId)).thenReturn(Optional.of(ApplicationState.SCALING_REQUESTED));
        when(asDB.application(appId)).thenReturn(Optional.of(new ApplicationInfo(appId,
                                                                                 ControllerTestUtils.appSpec(),
                                                                                 1,
                                                                                 null,
                                                                                 null)));
        ensureFailure(
                validator.validateOperation(engine,
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
        val validator = createValidator();
        val res = validator.validateOperation(engine,
                                              new ApplicationCreateOperation(ControllerTestUtils.appSpec(1),
                                                                    1,
                                                                    ClusterOpSpec.DEFAULT));
        ensureSuccess(res);
    }

    @Test
    void testCreateFailsForBrokenPlacementRule() {
        val validator = createValidator();
        val spec = ControllerTestUtils.appSpec(1)
                .withPlacementPolicy(new RuleBasedPlacementPolicy("(true &&"));
        val res = validator.validateSpec(spec);
        assertEquals(ValidationStatus.FAILURE, res.getStatus());
        assertEquals(1, res.getMessages().size());
        assertTrue(res.getMessages().get(0).startsWith("Placement rule failed to compile: (true && - "));
    }

    @Test
    void testCreatePassesForValidPlacementRule() {
        val validator = createValidator();
        val spec = ControllerTestUtils.appSpec(1)
                .withPlacementPolicy(new RuleBasedPlacementPolicy("ctx.allocation.node.executorId == 'Ex1'"));
        val res = validator.validateSpec(spec);
        assertEquals(ValidationStatus.SUCCESS, res.getStatus(), () -> "Messages: " + res.getMessages());
    }

    @Test
    void testCreateNoAppId() {
        val validator = createValidator();
        val res = validator.validateOperation(engine,
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
        val validator = createValidator();
        when(engine.exists(anyString())).thenReturn(true);
        val res = validator.validateOperation(engine,
                                              new ApplicationCreateOperation(ControllerTestUtils.appSpec(1),
                                                                    1,
                                                                    ClusterOpSpec.DEFAULT));
        ensureFailure(res, "App TEST_SPEC-00001 already exists");
    }

    @Test
    void testCreateInvalidHealthCheckPortName() {
        val validator = createValidator();
        val spec = ControllerTestUtils.appSpec(1)
                .withHealthcheck(new CheckSpec(new HTTPCheckModeSpec(Protocol.HTTP,
                                                                     "Invalid",
                                                                     "/",
                                                                     GET,
                                                                     Set.of(200),
                                                                     null,
                                                                     Duration.seconds(1),
                                                                     false),
                                               Duration.seconds(1),
                                               Duration.seconds(1),
                                               3,
                                               Duration.seconds(1)));
        val res = validator.validateOperation(engine,
                                              new ApplicationCreateOperation(spec,
                                                                    1,
                                                                    ClusterOpSpec.DEFAULT));
        ensureFailure(res, "Invalid port name for health check: Invalid. Available ports: [main]");
    }

    @Test
    void testCreateInvalidReadinessCheckPortName() {
        val validator = createValidator();
        val spec = ControllerTestUtils.appSpec(1)
                .withReadiness(new CheckSpec(new HTTPCheckModeSpec(Protocol.HTTP,
                                                                   "Invalid",
                                                                   "/",
                                                                   GET,
                                                                   Set.of(200),
                                                                   null,
                                                                   Duration.seconds(1),
                                                                   false),
                                             Duration.seconds(1),
                                             Duration.seconds(1),
                                             3,
                                             Duration.seconds(1)));
        val res = validator.validateOperation(engine,
                                              new ApplicationCreateOperation(spec,
                                                                    1,
                                                                    ClusterOpSpec.DEFAULT));
        ensureFailure(res, "Invalid port name for health check: Invalid. Available ports: [main]");
    }

    @Test
    void testCreateMissingResourceRequirements() {
        val validator = createValidator();
        val spec = ControllerTestUtils.appSpec(1)
                .withResources(List.of());
        val res = validator.validateOperation(engine,
                                              new ApplicationCreateOperation(spec,
                                                                    1,
                                                                    ClusterOpSpec.DEFAULT));
        ensureFailure(res,
                      "Cpu requirements are mandatory",
                      "Memory requirements are mandatory"
                     );
    }

    @Test
    void testCreateMultipleGpuRequirements() {
        val validator = createValidator();
        val spec = ControllerTestUtils.appSpec(1)
                .withResources(List.of(new CPURequirement(1),
                                       new MemoryRequirement(512),
                                       new GPURequirement(1, new GpuType(GpuVendor.NVIDIA, "H100")),
                                       new GPURequirement(1, new GpuType(GpuVendor.NVIDIA, "A100"))));
        val res = validator.validateOperation(engine,
                                              new ApplicationCreateOperation(spec,
                                                                            1,
                                                                            ClusterOpSpec.DEFAULT));
        ensureFailure(res,
                      "Multiple GPU requirements specified in resource requirements. "
                              + "This is currently not supported.");
    }

    @Test
    void testCreateWrongPortInExposureSpec() {
        val validator = createValidator();
        val spec = ControllerTestUtils.appSpec(1)
                .withExposureSpec(List.of(new ExposureSpec("blah", "Invalid", ExposureMode.ALL)));
        val res = validator.validateOperation(engine,
                                              new ApplicationCreateOperation(spec,
                                                                    1,
                                                                    ClusterOpSpec.DEFAULT));
        ensureFailure(res,
                "No defined ports found with name 'Invalid'. Invalid port name used for vhost: blah. Known ports: [main]");
    }

    @Test
    void testCreateWrongPortInMultipleExposureSpecs() {
        val validator = new ApplicationCommandValidator(asDB, crDB, aiDB, new SecurityPolicyEnforcer(List.of(), ControllerOptions.DEFAULT), instanceScheduler,
                                                                           ControllerTestUtils.placementRuleEvaluator(MAPPER));
        val spec = ControllerTestUtils.appSpec(1)
                .withExposureSpec(List.of(new ExposureSpec("valid.appform.io", "main", ExposureMode.ALL),
                                          new ExposureSpec("wrong1.appform.io", "Invalid1", ExposureMode.ALL),
                                          new ExposureSpec("wrong2.appform.io", "Invalid2", ExposureMode.ALL)));
        val res = validator.validateOperation(engine,
                                              new ApplicationCreateOperation(spec,
                                                                    1,
                                                                    ClusterOpSpec.DEFAULT));
        ensureFailure(res,
                     "No defined ports found with name 'Invalid1'. Invalid port name used for vhost: wrong1.appform.io. Known ports: [main]",
                     "No defined ports found with name 'Invalid2'. Invalid port name used for vhost: wrong2.appform.io. Known ports: [main]");
    }

    @Test
    void testCreateWhitelistedDirMounts() {
        val controllerOptions = ControllerOptions.DEFAULT
                   .withAllowedMountDirs(List .of("/tmp"));
        val validator = new ApplicationCommandValidator(asDB,
                                                        crDB,
                                                        aiDB,
                                                        new SecurityPolicyEnforcer(List.of(),
                                                                                   controllerOptions),
                                                        instanceScheduler,
                                                        ControllerTestUtils.placementRuleEvaluator(MAPPER));
        {
            val spec = ControllerTestUtils.appSpec(1)
                    .withVolumes(List.of(new MountedVolume("/var/log",
                                                           "/var/log",
                                                           MountedVolume.MountMode.READ_WRITE)));
            val res = validator.validateOperation(engine,
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
            val res = validator.validateOperation(engine,
                                                  new ApplicationCreateOperation(spec,
                                                                                 1,
                                                                                 ClusterOpSpec.DEFAULT));
            ensureSuccess(res);
        }
    }

    @Test
    void testCmdlArgs() {

        {
            val controllerOptions = ControllerOptions.DEFAULT
                    .withDisableCmdlArgs(true);
            val validator = new ApplicationCommandValidator(asDB, crDB, aiDB,
                                                            new SecurityPolicyEnforcer(List.of(), controllerOptions),
                                                            instanceScheduler,
                                                            ControllerTestUtils.placementRuleEvaluator(MAPPER));
            val spec = ControllerTestUtils.appSpec(1)
                    .withArgs(List.of("random"));
            val res = validator.validateOperation(engine,
                                                  new ApplicationCreateOperation(spec,
                                                                        1,
                                                                        ClusterOpSpec.DEFAULT));
            ensureFailure(res, "Passing command line to containers is disabled on this cluster");
        }
        {
            val validator = createValidator();
            val spec = ControllerTestUtils.appSpec(1)
                    .withArgs(Collections.nCopies(1024, "0123456789"));
            val res = validator.validateOperation(engine,
                                                  new ApplicationCreateOperation(spec,
                                                                        1,
                                                                        ClusterOpSpec.DEFAULT));
            ensureFailure(res, "Maximum combined length of command line arguments can be 2048");
        }
    }

    @Test
    void testDeviceLoading() {

        val validator = createValidator();
        val spec = ControllerTestUtils.appSpec(1)
                .withDevices(List.of(DirectDeviceSpec.builder()
                        .pathOnHost("/dev/random")
                        .build()));
        val res = validator.validateOperation(engine,
                                              new ApplicationCreateOperation(spec,
                                                                             1,
                                                                             ClusterOpSpec.DEFAULT));
        ensureFailure(res,
                      "Device access is disabled. To enable, set enableRawDeviceAccess: true " +
                              "in controller options.");
    }

    @Test
    void testStartInstSuccess() {
        val validator = createValidator();
        val appId = "SomeAppId";
        when(engine.currentState(appId)).thenReturn(Optional.of(ApplicationState.MONITORING));
        when(asDB.application(appId)).thenReturn(Optional.of(new ApplicationInfo(appId,
                                                                                 ControllerTestUtils.appSpec(),
                                                                                 1,
                                                                                 null,
                                                                                 null)));
        when(aiDB.instanceCount(eq(appId), anySet())).thenReturn(2L);

        when(crDB.executorCount(true)).thenReturn(1L);
        when(instanceScheduler.checkPlacement(any(), anyInt()))
                .thenReturn(PlacementCheckResult.builder()
                                   .requested(1)
                                   .placed(1)
                                   .errors(List.of())
                                   .build());

        ensureSuccess(validator.validateOperation(
                engine, new ApplicationStartInstancesOperation(appId, 1, ClusterOpSpec.DEFAULT)));
    }

    @Test
    void testStartInstNoResources() {
        val validator = createValidator();
        val appId = "SomeAppId";
        when(engine.currentState(appId)).thenReturn(Optional.of(ApplicationState.MONITORING));
        when(asDB.application(appId)).thenReturn(Optional.of(new ApplicationInfo(appId,
                                                                                 ControllerTestUtils.appSpec(),
                                                                                 1,
                                                                                 null,
                                                                                 null)));

        when(crDB.executorCount(true)).thenReturn(1L);
        when(instanceScheduler.checkPlacement(any(), anyInt()))
                .thenReturn(PlacementCheckResult.builder()
                                   .requested(100)
                                   .placed(3)
                                   .errors(List.of(
                                           "Cluster can place only 3 of 100 requested instances for SomeAppId"))
                                   .build());
        ensureFailure(validator.validateOperation(
                              engine, new ApplicationStartInstancesOperation(appId, 100, ClusterOpSpec.DEFAULT)),
                      "Cluster can place only 3 of 100 requested instances for SomeAppId");
    }
    @Test
    void testStartInstValidateOperationMaxCorePerNode() {
        val validator = createValidator();
        val appId = "SomeAppId";
        when(engine.currentState(appId)).thenReturn(Optional.of(ApplicationState.MONITORING));
        val appSpec = ControllerTestUtils.appSpec()
                .withResources(List.of(new CPURequirement(6), new MemoryRequirement(100)));
        when(asDB.application(appId)).thenReturn(Optional.of(new ApplicationInfo(appId,
                                                                                 appSpec,
                                                                                 1,
                                                                                 null,
                                                                                 null)));

        when(crDB.executorCount(true)).thenReturn(4L);
        when(instanceScheduler.checkPlacement(any(), anyInt()))
                .thenReturn(PlacementCheckResult.builder()
                                   .requested(1)
                                   .placed(0)
                                   .errors(List.of(
                                           "Cluster can place only 0 of 1 requested instances for SomeAppId",
                                           "Required cores exceed the maximum free cores on a single "
                                                   + "NUMA node in the cluster. Required: 6 Max free: 5"))
                                   .build());

        ensureFailure(validator.validateOperation(
                              engine, new ApplicationStartInstancesOperation(appId, 1, ClusterOpSpec.DEFAULT)),
                      "Cluster can place only 0 of 1 requested instances for SomeAppId",
                      "Required cores exceed the maximum free cores on a single "
                              + "NUMA node in the cluster. Required: 6 Max free: 5");
    }

    @Test
    void testScaleSuccess() {
        val validator = createValidator();
        val appId = "SomeAppId";
        when(engine.currentState(appId)).thenReturn(Optional.of(ApplicationState.MONITORING));
        when(asDB.application(appId)).thenReturn(Optional.of(new ApplicationInfo(appId,
                                                                                 ControllerTestUtils.appSpec(),
                                                                                 1,
                                                                                 null,
                                                                                 null)));
        when(crDB.executorCount(true)).thenReturn(1L);
        when(instanceScheduler.checkPlacement(any(), anyInt()))
                .thenReturn(PlacementCheckResult.builder()
                                   .requested(1)
                                   .placed(1)
                                   .errors(List.of())
                                   .build());

        ensureSuccess(validator.validateOperation(
                engine, new ApplicationScaleOperation(appId, 1, ClusterOpSpec.DEFAULT)));
    }


    @Test
    void testScaleInvalidId() {
        val validator = createValidator();
        val appId = "SomeAppId";
        when(engine.currentState(anyString())).thenReturn(Optional.of(ApplicationState.MONITORING));
        when(asDB.application(appId)).thenReturn(Optional.empty());
        when(crDB.executorCount(true)).thenReturn(1L);
        when(crDB.currentSnapshot(true))
                .thenReturn(List.of(ControllerTestUtils.executorHost(8000)));

        ensureFailure(validator.validateOperation(
                              engine, new ApplicationScaleOperation(appId, 1, ClusterOpSpec.DEFAULT)),
                      "No spec found for app SomeAppId");
    }

    @Test
    void testScaleNoResource() {
        val validator = createValidator();
        val appId = "SomeAppId";
        when(engine.currentState(anyString())).thenReturn(Optional.of(ApplicationState.MONITORING));
        when(asDB.application(appId)).thenReturn(Optional.of(new ApplicationInfo(appId,
                                                                                 ControllerTestUtils.appSpec(),
                                                                                 1,
                                                                                 null,
                                                                                 null)));
        when(crDB.executorCount(true)).thenReturn(1L);
        when(instanceScheduler.checkPlacement(any(), anyInt()))
                .thenReturn(PlacementCheckResult.builder()
                                   .requested(100)
                                   .placed(3)
                                   .errors(List.of(
                                           "Cluster can place only 3 of 100 requested instances for SomeAppId"))
                                   .build());

        ensureFailure(validator.validateOperation(
                              engine, new ApplicationScaleOperation(appId, 100, ClusterOpSpec.DEFAULT)),
                      "Cluster can place only 3 of 100 requested instances for SomeAppId");
    }

    @Test
    void testStopInstSuccess() {
        val validator = createValidator();
        val appId = "SomeAppId";
        when(engine.currentState(appId)).thenReturn(Optional.of(ApplicationState.RUNNING));
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

        ensureSuccess(validator.validateOperation(
                engine,
                new ApplicationStopInstancesOperation(appId, List.of("AI-00001"), false, ClusterOpSpec.DEFAULT)));
    }

    @Test
    void testStopInstInvalidId() {
        val validator = createValidator();
        val appId = "SomeAppId";
        when(engine.currentState(appId)).thenReturn(Optional.of(ApplicationState.RUNNING));
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

        ensureFailure(validator.validateOperation(
                              engine,
                              new ApplicationStopInstancesOperation(appId, List.of("Invalid"), false,
                                                                    ClusterOpSpec.DEFAULT)),
                      "App SomeAppId does not have any instances with the following ids: Invalid");
    }

    @Test
    void testReplaceInstSuccess() {
        val validator = createValidator();
        val appId = "SomeAppId";
        when(engine.currentState(appId)).thenReturn(Optional.of(ApplicationState.RUNNING));
        val spec = ControllerTestUtils.appSpec();
        when(asDB.application(appId)).thenReturn(Optional.of(new ApplicationInfo(appId,
                                                                                 spec,
                                                                                 1,
                                                                                 null,
                                                                                 null)));
        when(aiDB.instance(appId, "AI-00001"))
                .thenReturn(Optional.of(ControllerTestUtils.generateInstanceInfo(appId, spec, 1)));
        when(crDB.currentSnapshot(true))
                .thenReturn(List.of(ControllerTestUtils.executorHost(8000)));

        ensureSuccess(validator.validateOperation(
                engine,
                new ApplicationReplaceInstancesOperation(appId, Set.of("AI-00001"), false, ClusterOpSpec.DEFAULT)));
    }

    @Test
    void testReplaceInstInvalidId() {
        val validator = createValidator();
        val appId = "SomeAppId";
        when(engine.currentState(appId)).thenReturn(Optional.of(ApplicationState.RUNNING));
        val spec = ControllerTestUtils.appSpec();
        when(asDB.application(appId)).thenReturn(Optional.of(new ApplicationInfo(appId,
                                                                                 spec,
                                                                                 1,
                                                                                 null,
                                                                                 null)));
        when(crDB.currentSnapshot(true))
                .thenReturn(List.of(ControllerTestUtils.executorHost(8000)));

        ensureFailure(validator.validateOperation(
                              engine,
                              new ApplicationReplaceInstancesOperation(appId, Set.of("Invalid"),
                                                                       false,
                                                                       ClusterOpSpec.DEFAULT)),
                      "There are no replaceable healthy instances with ids: [Invalid]");
    }

    @Test
    void testDestroyAppSuccess() {
        val validator = createValidator();
        val appId = "SomeAppId";
        when(engine.currentState(appId)).thenReturn(Optional.of(ApplicationState.MONITORING));
        val spec = ControllerTestUtils.appSpec();
        when(asDB.application(appId)).thenReturn(Optional.of(new ApplicationInfo(appId,
                                                                                 spec,
                                                                                 1,
                                                                                 null,
                                                                                 null)));
        ensureSuccess(validator.validateOperation(
                engine,
                new ApplicationDestroyOperation(appId, ClusterOpSpec.DEFAULT)));
    }

    @Test
    void testSuspendAppSuccess() {
        val validator = createValidator();
        val appId = "SomeAppId";
        when(engine.currentState(appId)).thenReturn(Optional.of(ApplicationState.RUNNING));
        val spec = ControllerTestUtils.appSpec();
        when(asDB.application(appId)).thenReturn(Optional.of(new ApplicationInfo(appId,
                                                                                 spec,
                                                                                 1,
                                                                                 null,
                                                                                 null)));
        ensureSuccess(validator.validateOperation(
                engine,
                new ApplicationSuspendOperation(appId, ClusterOpSpec.DEFAULT)));
    }

    @Test
    void testRecoverAppSuccess() {
        val validator = createValidator();
        val appId = "SomeAppId";
        when(engine.currentState(appId)).thenReturn(Optional.of(ApplicationState.RUNNING));
        val spec = ControllerTestUtils.appSpec();
        when(asDB.application(appId)).thenReturn(Optional.of(new ApplicationInfo(appId,
                                                                                 spec,
                                                                                 1,
                                                                                 null,
                                                                                 null)));
        ensureSuccess(validator.validateOperation(
                engine,
                new ApplicationRecoverOperation(appId)));
    }

    private void testNoOpState(ApplicationState state, String errorMessage) {
        val validator = createValidator();
        val appId = "SomeAppId";
        when(engine.currentState(appId)).thenReturn(Optional.of(state));
        when(asDB.application(appId)).thenReturn(Optional.of(new ApplicationInfo(appId,
                                                                                 ControllerTestUtils.appSpec(),
                                                                                 1,
                                                                                 null,
                                                                                 null)));
        ensureFailure(
                validator.validateOperation(engine,
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

    private ApplicationCommandValidator createValidator() {
        return new ApplicationCommandValidator(asDB,
                                               crDB,
                                               aiDB,
                                               new SecurityPolicyEnforcer(List.of(), ControllerOptions.DEFAULT),
                                               instanceScheduler,
                                               ControllerTestUtils.placementRuleEvaluator(MAPPER));
    }

    @Test
    void testCreateCustomPolicyAllowsVolume() {
        val profile = ContainerSecurityProfile.builder()
                .policyName("allow-var-log")
                .allowedMountDirs(List.of("/var/log"))
                .build();
        val enforcer = enforcerWithPolicy("SERVICE/TEST_SPEC", profile);
        val validator = createValidatorWithEnforcer(enforcer);
        val spec = ControllerTestUtils.appSpec(1)
                .withVolumes(List.of(new MountedVolume("/var/log", "/var/log",
                                                       MountedVolume.MountMode.READ_ONLY)));
        val res = validator.validateOperation(engine,
                                              new ApplicationCreateOperation(
                                                      spec, 1, ClusterOpSpec.DEFAULT));
        ensureSuccess(res);
    }

    @Test
    void testCreateCustomPolicyBlocksCmdlArgs() {
        val profile = ContainerSecurityProfile.builder()
                .policyName("no-cmdl")
                .disableCmdlArgs(true)
                .build();
        val enforcer = enforcerWithPolicy("SERVICE/*", profile);
        val validator = createValidatorWithEnforcer(enforcer);
        val spec = ControllerTestUtils.appSpec(1)
                .withArgs(List.of("--some-flag"));
        val res = validator.validateOperation(engine,
                                              new ApplicationCreateOperation(
                                                      spec, 1, ClusterOpSpec.DEFAULT));
        ensureFailure(res, "Passing command line to containers is disabled on this cluster");
    }

    @Test
    void testCreateCustomPolicyAllowsDevice() {
        val profile = ContainerSecurityProfile.builder()
                .policyName("allow-device")
                .enableRawDeviceAccess(true)
                .build();
        val enforcer = enforcerWithPolicy("SERVICE/*", profile);
        val validator = createValidatorWithEnforcer(enforcer);
        val spec = ControllerTestUtils.appSpec(1)
                .withDevices(List.of(DirectDeviceSpec.builder()
                                             .pathOnHost("/dev/random")
                                             .build()));
        val res = validator.validateOperation(engine,
                                              new ApplicationCreateOperation(
                                                      spec, 1, ClusterOpSpec.DEFAULT));
        ensureSuccess(res);
    }

    @Test
    void testCreatePolicyNoMatchFallback() {
        val profile = ContainerSecurityProfile.builder()
                .policyName("some-policy")
                .disableCmdlArgs(true)
                .build();
        val enforcer = enforcerWithPolicy("SERVICE/SomeOtherApp.*", profile);
        val validator = createValidatorWithEnforcer(enforcer);
        val spec = ControllerTestUtils.appSpec(1);
        val res = validator.validateOperation(engine,
                                              new ApplicationCreateOperation(
                                                      spec, 1, ClusterOpSpec.DEFAULT));
        ensureSuccess(res);
    }
    private ApplicationCommandValidator createValidatorWithEnforcer(
            SecurityPolicyEnforcer enforcer) {
        return new ApplicationCommandValidator(asDB, crDB, aiDB, enforcer, instanceScheduler, ControllerTestUtils.placementRuleEvaluator(MAPPER));
    }

    private SecurityPolicyEnforcer enforcerWithPolicy(
            String matchRule,
            ContainerSecurityProfile profile) {
        val config = ContainerSecurityPolicyConfig.builder()
                .match(List.of(matchRule))
                .profile(profile)
                .build();
        return new SecurityPolicyEnforcer(List.of(config), ControllerOptions.DEFAULT);
    }

}

