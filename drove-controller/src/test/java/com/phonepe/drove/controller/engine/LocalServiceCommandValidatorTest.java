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
import com.phonepe.drove.controller.statedb.LocalServiceStateDB;
import com.phonepe.drove.controller.utils.ControllerUtils;
import com.phonepe.drove.models.application.MountedVolume;
import com.phonepe.drove.models.application.PortType;
import com.phonepe.drove.models.application.checks.CheckSpec;
import com.phonepe.drove.models.application.checks.HTTPCheckModeSpec;
import com.phonepe.drove.models.application.devices.DirectDeviceSpec;
import com.phonepe.drove.models.application.placement.policies.AnyPlacementPolicy;
import com.phonepe.drove.models.application.placement.policies.LocalPlacementPolicy;
import com.phonepe.drove.models.info.resources.allocation.CPUAllocation;
import com.phonepe.drove.models.info.resources.allocation.MemoryAllocation;
import com.phonepe.drove.models.instance.InstancePort;
import com.phonepe.drove.models.instance.LocalInstanceInfo;
import com.phonepe.drove.models.instance.LocalServiceInstanceState;
import com.phonepe.drove.models.localservice.ActivationState;
import com.phonepe.drove.models.localservice.LocalServiceInfo;
import com.phonepe.drove.models.localservice.LocalServiceInstanceInfo;
import com.phonepe.drove.models.localservice.LocalServiceState;
import com.phonepe.drove.models.operation.ClusterOpSpec;
import com.phonepe.drove.models.operation.LocalServiceOperation;
import com.phonepe.drove.models.operation.localserviceops.*;
import io.dropwizard.util.Duration;
import lombok.val;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.*;
import java.util.stream.Stream;

import static com.phonepe.drove.controller.ControllerTestUtils.EXECUTOR_ID;
import static com.phonepe.drove.models.common.HTTPVerb.GET;
import static com.phonepe.drove.models.common.Protocol.HTTP;
import static com.phonepe.drove.models.localservice.LocalServiceState.ACTIVE;
import static com.phonepe.drove.models.localservice.LocalServiceState.INACTIVE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

/**
 * Tests {@link LocalServiceCommandValidator}
 */
class LocalServiceCommandValidatorTest extends ControllerTestBase {


    private final LocalServiceStateDB lsDB = mock(LocalServiceStateDB.class);
    private final ClusterResourcesDB crDB = mock(ClusterResourcesDB.class);

    private final LocalServiceLifecycleManagementEngine engine = mock(LocalServiceLifecycleManagementEngine.class);

    @BeforeEach
    void resetMocks() {
        reset(lsDB, crDB, engine);
    }

    @Test
    void testNoState() {
        val validator = new LocalServiceCommandValidator(lsDB, crDB, ControllerOptions.DEFAULT);
        val serviceId = "SomeServiceId";
        when(engine.currentState(serviceId)).thenReturn(Optional.empty());
        val res = validator.validate(engine, new LocalServiceDestroyOperation(serviceId));
        ensureFailure(res,
                      "No state found for local service: SomeServiceId"
                     );
    }

    @ParameterizedTest
    @EnumSource(value = LocalServiceState.class,
            names = {
                    "INIT",
                    "ACTIVATION_REQUESTED",
                    "DESTROY_REQUESTED",
                    "DEACTIVATION_REQUESTED",
                    "ADJUSTING_INSTANCES",
                    "REPLACING_INSTANCES",
                    "STOPPING_INSTANCES",
                    "DESTROYED",
            })
    void testNoOpsInRelevantStates(LocalServiceState testState) {
        val validator = new LocalServiceCommandValidator(lsDB, crDB, ControllerOptions.DEFAULT);
        val serviceId = "SomeServiceId";
        when(engine.currentState(serviceId)).thenReturn(Optional.of(testState));
        when(lsDB.service(serviceId)).thenReturn(Optional.of(new LocalServiceInfo(serviceId,
                                                                                  ControllerTestUtils.localServiceSpec(),
                                                                                  1,
                                                                                  ActivationState.ACTIVE,
                                                                                  null,
                                                                                  null)));
        ensureFailure(
                validator.validate(engine,
                                   new LocalServiceStopInstancesOperation(serviceId,
                                                                          Set.of(),
                                                                          ClusterOpSpec.DEFAULT)),
                "No operations allowed for SomeServiceId as it is in %s state".formatted(testState));
    }

    @ParameterizedTest
    @MethodSource(value = {"invalidCommandGenerator"})
    void testInvalidOpsInRelevantStates(
            LocalServiceState testState,
            LocalServiceOperation operation) {
        val validator = new LocalServiceCommandValidator(lsDB, crDB, ControllerOptions.DEFAULT);
        val serviceId = "SomeServiceId";
        when(engine.currentState(serviceId)).thenReturn(Optional.of(testState));
        when(lsDB.service(serviceId)).thenReturn(Optional.of(new LocalServiceInfo(serviceId,
                                                                                  ControllerTestUtils.localServiceSpec(),
                                                                                  1,
                                                                                  ActivationState.ACTIVE,
                                                                                  null,
                                                                                  null)));
        ensureFailure(
                validator.validate(engine, operation),
                "Only %s allowed for local service SomeServiceId as it is in %s state"
                        .formatted(LocalServiceCommandValidator.validOpsForState(testState), testState));
    }

    @ParameterizedTest
    @MethodSource(value = {"validCommandGenerator"})
    void testValidOpsInRelevantStates(
            LocalServiceState testState,
            LocalServiceOperation operation) {
        val validator = new LocalServiceCommandValidator(lsDB, crDB, ControllerOptions.DEFAULT);
        val serviceId = "SomeServiceId";
        when(engine.currentState(serviceId)).thenReturn(Optional.of(testState));
        when(lsDB.service(serviceId)).thenReturn(Optional.of(new LocalServiceInfo(serviceId,
                                                                                  ControllerTestUtils.localServiceSpec(),
                                                                                  1,
                                                                                  ActivationState.ACTIVE,
                                                                                  null,
                                                                                  null)));
        ensureSuccess(validator.validate(engine, operation));
    }

    @Test
    void testCreateSuccess() {
        val validator = new LocalServiceCommandValidator(lsDB, crDB, ControllerOptions.DEFAULT);
        val res = validator.validate(engine,
                                     new LocalServiceCreateOperation(ControllerTestUtils.localServiceSpec(1),
                                                                     1));
        ensureSuccess(res);
    }


    @Test
    void testCreateNoLocalServiceId() {
        val validator = new LocalServiceCommandValidator(lsDB, crDB, ControllerOptions.DEFAULT);
        val res = validator.validate(engine,
                                     new LocalServiceCreateOperation(
                                             ControllerTestUtils.localServiceSpec(1)
                                                     .withName(null)
                                                     .withVersion(null),
                                             1));
        ensureFailure(res, "No local service id found in operation");
    }

    @Test
    void testCreateForExitingService() {
        val validator = new LocalServiceCommandValidator(lsDB, crDB, ControllerOptions.DEFAULT);
        when(engine.exists(anyString())).thenReturn(true);
        val res = validator.validate(engine,
                                     new LocalServiceCreateOperation(ControllerTestUtils.localServiceSpec(1), 1));
        ensureFailure(res, "Local service TEST_SPEC-00001 already exists");
    }

    @Test
    void testCreateInvalidHealthCheckPortName() {
        val validator = new LocalServiceCommandValidator(lsDB, crDB, ControllerOptions.DEFAULT);
        val spec = ControllerTestUtils.localServiceSpec(1)
                .withHealthcheck(new CheckSpec(new HTTPCheckModeSpec(HTTP,
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
        val res = validator.validate(engine, new LocalServiceCreateOperation(spec, 1));
        ensureFailure(res, "Invalid port name for health check: Invalid. Available ports: [main]");
    }

    @Test
    void testCreateInvalidReadinessCheckPortName() {
        val validator = new LocalServiceCommandValidator(lsDB, crDB, ControllerOptions.DEFAULT);
        val spec = ControllerTestUtils.localServiceSpec(1)
                .withReadiness(new CheckSpec(new HTTPCheckModeSpec(HTTP,
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
        val res = validator.validate(engine,
                                     new LocalServiceCreateOperation(spec, 1));
        ensureFailure(res, "Invalid port name for health check: Invalid. Available ports: [main]");
    }

    @Test
    void testCreateMissingResourceRequirements() {
        val validator = new LocalServiceCommandValidator(lsDB, crDB, ControllerOptions.DEFAULT);
        val spec = ControllerTestUtils.localServiceSpec(1)
                .withResources(List.of());
        val res = validator.validate(engine,
                                     new LocalServiceCreateOperation(spec, 1));
        ensureFailure(res,
                      "Cpu requirements are mandatory",
                      "Memory requirements are mandatory"
                     );
    }

    @Test
    void testCreateWhitelistedDirMounts() {
        val validator = new LocalServiceCommandValidator(lsDB, crDB,
                                                         ControllerOptions.DEFAULT
                                                                 .withAllowedMountDirs(List.of("/tmp")));
        {
            val spec = ControllerTestUtils.localServiceSpec(1)
                    .withVolumes(List.of(new MountedVolume("/var/log",
                                                           "/var/log",
                                                           MountedVolume.MountMode.READ_WRITE)));
            val res = validator.validate(engine,
                                         new LocalServiceCreateOperation(spec, 1));
            ensureFailure(res,
                          "Volume mount requested on non whitelisted host directory: /var/log"
                         );
        }
        {
            val spec = ControllerTestUtils.localServiceSpec(1)
                    .withVolumes(List.of(new MountedVolume("/var/log",
                                                           "/tmp/log",
                                                           MountedVolume.MountMode.READ_WRITE)));
            val res = validator.validate(engine,
                                         new LocalServiceCreateOperation(spec, 1));
            ensureSuccess(res);
        }
    }

    @Test
    void testCmdlArgs() {

        {
            val validator = new LocalServiceCommandValidator(lsDB, crDB,
                                                             ControllerOptions.DEFAULT
                                                                     .withDisableCmdlArgs(true));
            val spec = ControllerTestUtils.localServiceSpec(1)
                    .withArgs(List.of("random"));
            val res = validator.validate(engine,
                                         new LocalServiceCreateOperation(spec, 1));
            ensureFailure(res, "Passing command line to containers is disabled on this cluster");
        }
        {
            val validator = new LocalServiceCommandValidator(lsDB, crDB, ControllerOptions.DEFAULT);
            val spec = ControllerTestUtils.localServiceSpec(1)
                    .withArgs(Collections.nCopies(1024, "0123456789"));
            val res = validator.validate(engine,
                                         new LocalServiceCreateOperation(spec, 1));
            ensureFailure(res, "Maximum combined length of command line arguments can be 2048");
        }
    }

    @Test
    void testDeviceLoading() {
        {
            val validator = new LocalServiceCommandValidator(lsDB, crDB, ControllerOptions.DEFAULT);
            val spec = ControllerTestUtils.localServiceSpec(1)
                    .withDevices(List.of(DirectDeviceSpec.builder()
                                                 .pathOnHost("/dev/random")
                                                 .build()));
            val res = validator.validate(engine,
                                         new LocalServiceCreateOperation(spec, 1));
            ensureFailure(res, "Device access is disabled. To enable, set enableRawDeviceAccess: true " +
                    "in controller options.");
        }
    }

    @Test
    void testFailOnlyLocalPolicy() {
        {
            val validator = new LocalServiceCommandValidator(lsDB, crDB, ControllerOptions.DEFAULT);
            val spec = ControllerTestUtils.localServiceSpec(1)
                    .withPlacementPolicy(new AnyPlacementPolicy());
            val res = validator.validate(engine,
                                         new LocalServiceCreateOperation(spec, 1));
            ensureFailure(res, "Only local placement is allowed for local services");
        }
    }

    @Test
    void testFailLocalPolicyNohostLevel() {
        val validator = new LocalServiceCommandValidator(lsDB, crDB, ControllerOptions.DEFAULT);
        val serviceId = "SomeServiceId";
        when(engine.currentState(serviceId)).thenReturn(Optional.of(INACTIVE));
        when(lsDB.service(serviceId)).thenReturn(Optional.of(new LocalServiceInfo(serviceId,
                                                                                  ControllerTestUtils.localServiceSpec()
                                                                                          .withPlacementPolicy(new LocalPlacementPolicy(
                                                                                                  true)),
                                                                                  1,
                                                                                  ActivationState.INACTIVE,
                                                                                  null,
                                                                                  null)));

        val res = validator.validate(engine,
                                     new LocalServiceUpdateInstanceCountOperation(serviceId, 1));
        ensureFailure(res, "Update is allowed for services that do not have Host Level option set");
    }

    @Test
    void testReplaceInstancesInvalidIds() {
        val validator = new LocalServiceCommandValidator(lsDB, crDB, ControllerOptions.DEFAULT);
        val serviceId = "SomeServiceId";
        when(engine.currentState(serviceId)).thenReturn(Optional.of(ACTIVE));
        when(lsDB.service(serviceId)).thenReturn(Optional.of(new LocalServiceInfo(serviceId,
                                                                                  ControllerTestUtils.localServiceSpec(),
                                                                                  1,
                                                                                  ActivationState.ACTIVE,
                                                                                  null,
                                                                                  null)));
        when(lsDB.instance(eq(serviceId), anyString())).thenReturn(Optional.empty());
        ensureFailure(validator.validate(engine,
                                         new LocalServiceReplaceInstancesOperation(serviceId,
                                                                                   Set.of("blah"),
                                                                                   false,
                                                                                   ClusterOpSpec.DEFAULT)),
                      "There are no replaceable healthy instances with ids: [blah]");
    }

    @Test
    void testReplaceInstancesInvalidInstanceState() {
        val validator = new LocalServiceCommandValidator(lsDB, crDB, ControllerOptions.DEFAULT);
        val spec = ControllerTestUtils.localServiceSpec();
        val serviceId = ControllerUtils.deployableObjectId(spec);
        when(engine.currentState(serviceId)).thenReturn(Optional.of(ACTIVE));
        when(lsDB.service(serviceId)).thenReturn(Optional.of(new LocalServiceInfo(serviceId,
                                                                                  ControllerTestUtils.localServiceSpec(),
                                                                                  1,
                                                                                  ActivationState.ACTIVE,
                                                                                  null,
                                                                                  null)));
        when(lsDB.instance(eq(serviceId), anyString()))
                .thenReturn(Optional.of(new LocalServiceInstanceInfo(serviceId,
                                                                     spec.getName(),
                                                                     "SI-1",
                                                                     EXECUTOR_ID,
                                                                     new LocalInstanceInfo("localhost",
                                                                                           Collections.singletonMap(
                                                                                                   "main",
                                                                                                   new InstancePort(
                                                                                                           8000,
                                                                                                           32000,
                                                                                                           PortType.HTTP))),
                                                                     List.of(new CPUAllocation(Map.of(0, Set.of(1))),
                                                                             new MemoryAllocation(Map.of(0, 512L))),
                                                                     LocalServiceInstanceState.PROVISIONING,
                                                                     Collections.emptyMap(),
                                                                     "",
                                                                     null,
                                                                     null)));
        ensureFailure(validator.validate(engine,
                                         new LocalServiceReplaceInstancesOperation(serviceId,
                                                                                   Set.of("blah"),
                                                                                   false,
                                                                                   ClusterOpSpec.DEFAULT)),
                      "There are no replaceable healthy instances with ids: [blah]");
    }

    @Test
    void testReplaceInstancesWithIdSuccess() {
        val validator = new LocalServiceCommandValidator(lsDB, crDB, ControllerOptions.DEFAULT);
        val spec = ControllerTestUtils.localServiceSpec();
        val serviceId = ControllerUtils.deployableObjectId(spec);
        when(engine.currentState(serviceId)).thenReturn(Optional.of(ACTIVE));
        when(lsDB.service(serviceId)).thenReturn(Optional.of(new LocalServiceInfo(serviceId,
                                                                                  ControllerTestUtils.localServiceSpec(),
                                                                                  1,
                                                                                  ActivationState.ACTIVE,
                                                                                  null,
                                                                                  null)));
        when(lsDB.instance(serviceId, "SI-1"))
                .thenReturn(Optional.of(new LocalServiceInstanceInfo(serviceId,
                                                                     spec.getName(),
                                                                     "SI-1",
                                                                     EXECUTOR_ID,
                                                                     new LocalInstanceInfo("localhost",
                                                                                           Collections.singletonMap(
                                                                                                   "main",
                                                                                                   new InstancePort(
                                                                                                           8000,
                                                                                                           32000,
                                                                                                           PortType.HTTP))),
                                                                     List.of(new CPUAllocation(Map.of(0, Set.of(1))),
                                                                             new MemoryAllocation(Map.of(0, 512L))),
                                                                     LocalServiceInstanceState.HEALTHY,
                                                                     Collections.emptyMap(),
                                                                     "",
                                                                     null,
                                                                     null)));
        ensureSuccess(validator.validate(engine,
                                         new LocalServiceReplaceInstancesOperation(serviceId,
                                                                                   Set.of("SI-1"),
                                                                                   false,
                                                                                   ClusterOpSpec.DEFAULT)));
    }

    @Test
    void testStopInstancesInvalidIds() {
        val validator = new LocalServiceCommandValidator(lsDB, crDB, ControllerOptions.DEFAULT);
        val serviceId = "SomeServiceId";
        when(engine.currentState(serviceId)).thenReturn(Optional.of(ACTIVE));
        when(lsDB.service(serviceId)).thenReturn(Optional.of(new LocalServiceInfo(serviceId,
                                                                                  ControllerTestUtils.localServiceSpec(),
                                                                                  1,
                                                                                  ActivationState.ACTIVE,
                                                                                  null,
                                                                                  null)));
        when(lsDB.instance(eq(serviceId), anyString())).thenReturn(Optional.empty());
        ensureFailure(validator.validate(engine,
                                         new LocalServiceStopInstancesOperation(serviceId,
                                                                                   Set.of("blah"),
                                                                                   ClusterOpSpec.DEFAULT)),
                      "There are no healthy instances with ids: [blah]");
    }

    @Test
    void testStopInstancesWithIdSuccess() {
        val validator = new LocalServiceCommandValidator(lsDB, crDB, ControllerOptions.DEFAULT);
        val spec = ControllerTestUtils.localServiceSpec();
        val serviceId = ControllerUtils.deployableObjectId(spec);
        when(engine.currentState(serviceId)).thenReturn(Optional.of(ACTIVE));
        when(lsDB.service(serviceId)).thenReturn(Optional.of(new LocalServiceInfo(serviceId,
                                                                                  ControllerTestUtils.localServiceSpec(),
                                                                                  1,
                                                                                  ActivationState.ACTIVE,
                                                                                  null,
                                                                                  null)));
        when(lsDB.instance(serviceId, "SI-1"))
                .thenReturn(Optional.of(new LocalServiceInstanceInfo(serviceId,
                                                                     spec.getName(),
                                                                     "SI-1",
                                                                     EXECUTOR_ID,
                                                                     new LocalInstanceInfo("localhost",
                                                                                           Collections.singletonMap(
                                                                                                   "main",
                                                                                                   new InstancePort(
                                                                                                           8000,
                                                                                                           32000,
                                                                                                           PortType.HTTP))),
                                                                     List.of(new CPUAllocation(Map.of(0, Set.of(1))),
                                                                             new MemoryAllocation(Map.of(0, 512L))),
                                                                     LocalServiceInstanceState.HEALTHY,
                                                                     Collections.emptyMap(),
                                                                     "",
                                                                     null,
                                                                     null)));
        ensureSuccess(validator.validate(engine,
                                         new LocalServiceStopInstancesOperation(serviceId,
                                                                                   Set.of("SI-1"),
                                                                                   ClusterOpSpec.DEFAULT)));
    }
//
//    @Test
//    void testStartInstSuccess() {
//        val validator = new LocalServiceCommandValidator(lsDB, crDB, ControllerOptions.DEFAULT);
//        val serviceId = "SomeServiceId";
//        when(engine.currentState(serviceId)).thenReturn(Optional.of(ApplicationState.MONITORING));
//        when(lsDB.application(serviceId)).thenReturn(Optional.of(new LocalServiceInfo(serviceId,
//                                                                                 ControllerTestUtils
//                                                                                 .localServiceSpec(),
//                                                                                 1,
//                                                                                 null,
//                                                                                 null)));
//        when(aiDB.instanceCount(eq(serviceId), anySet())).thenReturn(2L);
//
//        when(crDB.executorCount(true)).thenReturn(1L);
//        when(crDB.currentSnapshot(true))
//                .thenReturn(List.of(ControllerTestUtils.executorHost(8000)));
//
//        ensureSuccess(validator.validate(
//                engine, new ApplicationStartInstancesOperation(serviceId, 1, ClusterOpSpec.DEFAULT)));
//    }
//
//    @Test
//    void testStartInstNoResources() {
//        val validator = new LocalServiceCommandValidator(lsDB, crDB, ControllerOptions.DEFAULT);
//        val serviceId = "SomeServiceId";
//        when(engine.currentState(serviceId)).thenReturn(Optional.of(ApplicationState.MONITORING));
//        when(lsDB.application(serviceId)).thenReturn(Optional.of(new LocalServiceInfo(serviceId,
//                                                                                 ControllerTestUtils
//                                                                                 .localServiceSpec(),
//                                                                                 1,
//                                                                                 null,
//                                                                                 null)));
//
//        when(crDB.executorCount(true)).thenReturn(1L);
//        when(crDB.currentSnapshot(true))
//                .thenReturn(List.of(ControllerTestUtils.executorHost(8000)));
//
//        ensureFailure(validator.validate(
//                              engine, new ApplicationStartInstancesOperation(serviceId, 100, ClusterOpSpec.DEFAULT)),
//                      "Cluster does not have enough CPU. Required: 100 Available: 3",
//                      "Cluster does not have enough Memory. Required: 51200 Available: 8448");
//    }
//
//    @Test
//    void testStartInstValidateMaxCorePerNode() {
//        val validator = new LocalServiceCommandValidator(lsDB, crDB, ControllerOptions.DEFAULT);
//        val serviceId = "SomeServiceId";
//        when(engine.currentState(serviceId)).thenReturn(Optional.of(ApplicationState.MONITORING));
//        val localServiceSpec = ControllerTestUtils.localServiceSpec()
//                .withResources(List.of(new CPURequirement(6), new MemoryRequirement(100)));
//        when(lsDB.application(serviceId)).thenReturn(Optional.of(new LocalServiceInfo(serviceId,
//                                                                                 localServiceSpec,
//                                                                                 1,
//                                                                                 null,
//                                                                                 null)));
//
//        when(crDB.executorCount(true)).thenReturn(4L);
//        when(crDB.currentSnapshot(true))
//                .thenReturn(IntStream.range(0, 3)
//                                    .mapToObj(i -> ControllerTestUtils.executorHost(i, 8000, List.of(), List.of()))
//                                    .toList());
//
//        ensureFailure(validator.validate(
//                              engine, new ApplicationStartInstancesOperation(serviceId, 1, ClusterOpSpec.DEFAULT)),
//                      "Required cores exceeds the maximum core available on a single NUMA node in the cluster. " +
//                              "Required: 6 Max: 5");
//    }
//
//    @Test
//    void testScaleSuccess() {
//        val validator = new LocalServiceCommandValidator(lsDB, crDB, ControllerOptions.DEFAULT);
//        val serviceId = "SomeServiceId";
//        when(engine.currentState(serviceId)).thenReturn(Optional.of(ApplicationState.MONITORING));
//        when(lsDB.application(serviceId)).thenReturn(Optional.of(new LocalServiceInfo(serviceId,
//                                                                                 ControllerTestUtils
//                                                                                 .localServiceSpec(),
//                                                                                 1,
//                                                                                 null,
//                                                                                 null)));
//        when(aiDB.instanceCount(eq(serviceId), anySet())).thenReturn(2L);
//
//        when(crDB.currentSnapshot(true))
//                .thenReturn(List.of(ControllerTestUtils.executorHost(8000)));
//
//        ensureSuccess(validator.validate(
//                engine, new ApplicationScaleOperation(serviceId, 1, ClusterOpSpec.DEFAULT)));
//    }
//
//    @Test
//    void testScaleInvalidId() {
//        val validator = new LocalServiceCommandValidator(lsDB, crDB, ControllerOptions.DEFAULT);
//        val serviceId = "SomeServiceId";
//        when(engine.currentState(anyString())).thenReturn(Optional.of(ApplicationState.MONITORING));
//        when(lsDB.application(serviceId)).thenReturn(Optional.empty());
//        when(crDB.executorCount(true)).thenReturn(1L);
//        when(crDB.currentSnapshot(true))
//                .thenReturn(List.of(ControllerTestUtils.executorHost(8000)));
//
//        ensureFailure(validator.validate(
//                              engine, new ApplicationScaleOperation(serviceId, 1, ClusterOpSpec.DEFAULT)),
//                      "No spec found for app SomeServiceId");
//    }
//
//    @Test
//    void testScaleNoResource() {
//        val validator = new LocalServiceCommandValidator(lsDB, crDB, ControllerOptions.DEFAULT);
//        val serviceId = "SomeServiceId";
//        when(engine.currentState(anyString())).thenReturn(Optional.of(ApplicationState.MONITORING));
//        when(lsDB.application(serviceId)).thenReturn(Optional.of(new LocalServiceInfo(serviceId,
//                                                                                 ControllerTestUtils
//                                                                                 .localServiceSpec(),
//                                                                                 1,
//                                                                                 null,
//                                                                                 null)));
//        when(crDB.executorCount(true)).thenReturn(1L);
//        when(crDB.currentSnapshot(true))
//                .thenReturn(List.of(ControllerTestUtils.executorHost(8000)));
//
//        ensureFailure(validator.validate(
//                              engine, new ApplicationScaleOperation(serviceId, 100, ClusterOpSpec.DEFAULT)),
//                      "Cluster does not have enough CPU. Required: 100 Available: 3",
//                      "Cluster does not have enough Memory. Required: 51200 Available: 8448");
//    }
//
//    @Test
//    void testStopInstSuccess() {
//        val validator = new LocalServiceCommandValidator(lsDB, crDB, ControllerOptions.DEFAULT);
//        val serviceId = "SomeServiceId";
//        when(engine.currentState(serviceId)).thenReturn(Optional.of(ApplicationState.RUNNING));
//        val spec = ControllerTestUtils.localServiceSpec();
//        when(lsDB.application(serviceId)).thenReturn(Optional.of(new LocalServiceInfo(serviceId,
//                                                                                 spec,
//                                                                                 1,
//                                                                                 null,
//                                                                                 null)));
//        when(aiDB.activeInstances(serviceId, 0, Integer.MAX_VALUE))
//                .thenReturn(List.of(ControllerTestUtils.generateInstanceInfo(serviceId, spec, 1),
//                                    ControllerTestUtils.generateInstanceInfo(serviceId, spec, 2)));
//        when(crDB.currentSnapshot(true))
//                .thenReturn(List.of(ControllerTestUtils.executorHost(8000)));
//
//        ensureSuccess(validator.validate(
//                engine,
//                new ApplicationStopInstancesOperation(serviceId, List.of("AI-00001"), false, ClusterOpSpec.DEFAULT)));
//    }
//
//    @Test
//    void testStopInstInvalidId() {
//        val validator = new LocalServiceCommandValidator(lsDB, crDB, ControllerOptions.DEFAULT);
//        val serviceId = "SomeServiceId";
//        when(engine.currentState(serviceId)).thenReturn(Optional.of(ApplicationState.RUNNING));
//        val spec = ControllerTestUtils.localServiceSpec();
//        when(lsDB.application(serviceId)).thenReturn(Optional.of(new LocalServiceInfo(serviceId,
//                                                                                 spec,
//                                                                                 1,
//                                                                                 null,
//                                                                                 null)));
//        when(aiDB.activeInstances(serviceId, 0, Integer.MAX_VALUE))
//                .thenReturn(List.of(ControllerTestUtils.generateInstanceInfo(serviceId, spec, 1),
//                                    ControllerTestUtils.generateInstanceInfo(serviceId, spec, 2)));
//        when(crDB.currentSnapshot(true))
//                .thenReturn(List.of(ControllerTestUtils.executorHost(8000)));
//
//        ensureFailure(validator.validate(
//                              engine,
//                              new ApplicationStopInstancesOperation(serviceId, List.of("Invalid"), false,
//                                                                    ClusterOpSpec.DEFAULT)),
//                      "App SomeServiceId does not have any instances with the following ids: Invalid");
//    }
//
//    @Test
//    void testReplaceInstSuccess() {
//        val validator = new LocalServiceCommandValidator(lsDB, crDB, ControllerOptions.DEFAULT);
//        val serviceId = "SomeServiceId";
//        when(engine.currentState(serviceId)).thenReturn(Optional.of(ApplicationState.RUNNING));
//        val spec = ControllerTestUtils.localServiceSpec();
//        when(lsDB.application(serviceId)).thenReturn(Optional.of(new LocalServiceInfo(serviceId,
//                                                                                 spec,
//                                                                                 1,
//                                                                                 null,
//                                                                                 null)));
//        when(aiDB.instance(serviceId, "AI-00001"))
//                .thenReturn(Optional.of(ControllerTestUtils.generateInstanceInfo(serviceId, spec, 1)));
//        when(crDB.currentSnapshot(true))
//                .thenReturn(List.of(ControllerTestUtils.executorHost(8000)));
//
//        ensureSuccess(validator.validate(
//                engine,
//                new ApplicationReplaceInstancesOperation(serviceId, Set.of("AI-00001"), false, ClusterOpSpec
//                .DEFAULT)));
//    }
//
//    @Test
//    void testReplaceInstInvalidId() {
//        val validator = new LocalServiceCommandValidator(lsDB, crDB, ControllerOptions.DEFAULT);
//        val serviceId = "SomeServiceId";
//        when(engine.currentState(serviceId)).thenReturn(Optional.of(ApplicationState.RUNNING));
//        val spec = ControllerTestUtils.localServiceSpec();
//        when(lsDB.application(serviceId)).thenReturn(Optional.of(new LocalServiceInfo(serviceId,
//                                                                                 spec,
//                                                                                 1,
//                                                                                 null,
//                                                                                 null)));
//        when(crDB.currentSnapshot(true))
//                .thenReturn(List.of(ControllerTestUtils.executorHost(8000)));
//
//        ensureFailure(validator.validate(
//                              engine,
//                              new ApplicationReplaceInstancesOperation(serviceId, Set.of("Invalid"),
//                                                                       false,
//                                                                       ClusterOpSpec.DEFAULT)),
//                      "There are no replaceable healthy instances with ids: [Invalid]");
//    }
//
//    @Test
//    void testDestroyAppSuccess() {
//        val validator = new LocalServiceCommandValidator(lsDB, crDB, ControllerOptions.DEFAULT);
//        val serviceId = "SomeServiceId";
//        when(engine.currentState(serviceId)).thenReturn(Optional.of(ApplicationState.MONITORING));
//        val spec = ControllerTestUtils.localServiceSpec();
//        when(lsDB.application(serviceId)).thenReturn(Optional.of(new LocalServiceInfo(serviceId,
//                                                                                 spec,
//                                                                                 1,
//                                                                                 null,
//                                                                                 null)));
//        ensureSuccess(validator.validate(
//                engine,
//                new ApplicationDestroyOperation(serviceId, ClusterOpSpec.DEFAULT)));
//    }
//
//    @Test
//    void testSuspendAppSuccess() {
//        val validator = new LocalServiceCommandValidator(lsDB, crDB, ControllerOptions.DEFAULT);
//        val serviceId = "SomeServiceId";
//        when(engine.currentState(serviceId)).thenReturn(Optional.of(ApplicationState.RUNNING));
//        val spec = ControllerTestUtils.localServiceSpec();
//        when(lsDB.application(serviceId)).thenReturn(Optional.of(new LocalServiceInfo(serviceId,
//                                                                                 spec,
//                                                                                 1,
//                                                                                 null,
//                                                                                 null)));
//        ensureSuccess(validator.validate(
//                engine,
//                new ApplicationSuspendOperation(serviceId, ClusterOpSpec.DEFAULT)));
//    }
//
//    @Test
//    void testRecoverAppSuccess() {
//        val validator = new LocalServiceCommandValidator(lsDB, crDB, ControllerOptions.DEFAULT);
//        val serviceId = "SomeServiceId";
//        when(engine.currentState(serviceId)).thenReturn(Optional.of(ApplicationState.RUNNING));
//        val spec = ControllerTestUtils.localServiceSpec();
//        when(lsDB.application(serviceId)).thenReturn(Optional.of(new LocalServiceInfo(serviceId,
//                                                                                 spec,
//                                                                                 1,
//                                                                                 null,
//                                                                                 null)));
//        ensureSuccess(validator.validate(
//                engine,
//                new ApplicationRecoverOperation(serviceId)));
//    }
//

    private static void ensureSuccess(ValidationResult res) {
        assertEquals(ValidationStatus.SUCCESS, res.getStatus(),
                     () -> "Messages: " + res.getMessages());
        assertEquals(List.of("Success"),
                     res.getMessages(), () -> "Messages: " + res.getMessages());
    }

    private static void ensureFailure(ValidationResult res, String... expectedError) {
        assertEquals(ValidationStatus.FAILURE, res.getStatus());
        assertEquals(Arrays.asList(expectedError),
                     res.getMessages(), () -> "Messages: " + res.getMessages());
    }

    private static Stream<Arguments> invalidCommandGenerator() {
        val serviceId = "SomeServiceId";
        return Stream.of(
                Arguments.of(
                        INACTIVE,
                        new LocalServiceStopInstancesOperation(serviceId, Set.of(), ClusterOpSpec.DEFAULT)),
                Arguments.of(
                        ACTIVE,
                        new LocalServiceDestroyOperation(serviceId)));
    }

    private static Stream<Arguments> validCommandGenerator() {
        val serviceId = "SomeServiceId";
        return Stream.of(
                Arguments.of(INACTIVE, new LocalServiceActivateOperation(serviceId)),
                Arguments.of(INACTIVE, new LocalServiceAdjustInstancesOperation(serviceId, ClusterOpSpec.DEFAULT)),
                Arguments.of(INACTIVE, new LocalServiceDestroyOperation(serviceId)),
                Arguments.of(INACTIVE, new LocalServiceUpdateInstanceCountOperation(serviceId, 2)),
                Arguments.of(ACTIVE, new LocalServiceDeactivateOperation(serviceId)),
                Arguments.of(ACTIVE, new LocalServiceAdjustInstancesOperation(serviceId, ClusterOpSpec.DEFAULT)),
                Arguments.of(ACTIVE,
                             new LocalServiceReplaceInstancesOperation(serviceId,
                                                                       Set.of(),
                                                                       false,
                                                                       ClusterOpSpec.DEFAULT)),
                Arguments.of(ACTIVE, new LocalServiceRestartOperation(serviceId, false, ClusterOpSpec.DEFAULT)),
                Arguments.of(ACTIVE, new LocalServiceStopInstancesOperation(serviceId, Set.of(), ClusterOpSpec.DEFAULT))
                        );
    }

}
