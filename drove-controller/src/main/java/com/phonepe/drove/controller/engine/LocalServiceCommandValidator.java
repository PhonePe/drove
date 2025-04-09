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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;
import com.phonepe.drove.controller.config.ControllerOptions;
import com.phonepe.drove.controller.statedb.LocalServiceStateDB;
import com.phonepe.drove.controller.statemachine.localservice.LocalServiceActionContext;
import com.phonepe.drove.models.application.PortSpec;
import com.phonepe.drove.models.application.placement.policies.LocalPlacementPolicy;
import com.phonepe.drove.models.application.requirements.ResourceRequirement;
import com.phonepe.drove.models.application.requirements.ResourceType;
import com.phonepe.drove.models.localservice.LocalServiceInfo;
import com.phonepe.drove.models.localservice.LocalServiceSpec;
import com.phonepe.drove.models.localservice.LocalServiceState;
import com.phonepe.drove.models.operation.LocalServiceOperation;
import com.phonepe.drove.models.operation.LocalServiceOperationType;
import com.phonepe.drove.models.operation.LocalServiceOperationVisitor;
import com.phonepe.drove.models.operation.localserviceops.*;
import com.phonepe.drove.statemachine.Action;
import io.appform.functionmetrics.MonitoredFunction;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.phonepe.drove.controller.utils.ControllerUtils.*;
import static com.phonepe.drove.models.instance.LocalServiceInstanceState.HEALTHY;
import static com.phonepe.drove.models.operation.LocalServiceOperationType.*;


/**
 *
 */
@Singleton
@Slf4j
@RequiredArgsConstructor(onConstructor_ = {@Inject})
public class LocalServiceCommandValidator implements CommandValidator<LocalServiceOperation,
        LocalServiceSpec, DeployableLifeCycleManagementEngine<LocalServiceInfo, LocalServiceSpec, LocalServiceOperation, LocalServiceState,
                LocalServiceActionContext, Action<LocalServiceInfo, LocalServiceState, LocalServiceActionContext,
                LocalServiceOperation>>
        > {

    private static final Map<LocalServiceState, Set<LocalServiceOperationType>> VALID_OPS_TABLE
            = ImmutableMap.<LocalServiceState, Set<LocalServiceOperationType>>builder()
            .put(LocalServiceState.INIT, Set.of())
            .put(LocalServiceState.ACTIVATION_REQUESTED, Set.of())
            .put(LocalServiceState.CONFIG_TESTING_REQUESTED, Set.of())
            .put(LocalServiceState.DESTROY_REQUESTED, Set.of())
            .put(LocalServiceState.EMERGENCY_DEACTIVATION_REQUESTED, Set.of(DEACTIVATE))
            .put(LocalServiceState.DEACTIVATION_REQUESTED, Set.of())
            .put(LocalServiceState.ADJUSTING_INSTANCES, Set.of())
            .put(LocalServiceState.REPLACING_INSTANCES, Set.of())
            .put(LocalServiceState.STOPPING_INSTANCES, Set.of())
            .put(LocalServiceState.DESTROYED, Set.of())
            .put(LocalServiceState.INACTIVE, Set.of(ACTIVATE,
                                                    DEPLOY_TEST_INSTANCE,
                                                    ADJUST_INSTANCES,
                                                    DESTROY,
                                                    UPDATE_INSTANCE_COUNT))
            .put(LocalServiceState.CONFIG_TESTING,
                 Set.of(ACTIVATE,
                        DEACTIVATE,
                        ADJUST_INSTANCES,
                        REPLACE_INSTANCES,
                        RESTART,
                        STOP_INSTANCES))
            .put(LocalServiceState.ACTIVE,
                 Set.of(DEACTIVATE,
                        UPDATE_INSTANCE_COUNT,
                        ADJUST_INSTANCES,
                        REPLACE_INSTANCES,
                        RESTART,
                        STOP_INSTANCES))
            .build();

    private final LocalServiceStateDB localServiceStateDB;
    private final ControllerOptions controllerOptions;

    @VisibleForTesting
    static List<LocalServiceOperationType> validOpsForState(final LocalServiceState state) {
        return VALID_OPS_TABLE.getOrDefault(state, Set.of()).stream().sorted(Comparator.comparing(Enum::name)).toList();
    }

    @Override
    public ValidationResult validateSpec(LocalServiceSpec spec) {
        val errs = new ArrayList<String>();
        val ports = spec.getExposedPorts()
                .stream()
                .collect(Collectors.toMap(PortSpec::getName, Function.identity()));
        validateCheckSpec(spec.getHealthcheck(), ports).ifPresent(errs::add);
        validateCheckSpec(spec.getReadiness(), ports).ifPresent(errs::add);
        val reqs = spec.getResources().stream().collect(Collectors.groupingBy(ResourceRequirement::getType,
                                                                              Collectors.counting()));
        if (!reqs.containsKey(ResourceType.CPU)) {
            errs.add("Cpu requirements are mandatory");
        }
        if (!reqs.containsKey(ResourceType.MEMORY)) {
            errs.add("Memory requirements are mandatory");
        }
        errs.addAll(ensureWhitelistedVolumes(spec.getVolumes(), controllerOptions));
        errs.addAll(ensureCmdlArgs(spec.getArgs(), controllerOptions));
        errs.addAll(checkDeviceDisabled(spec.getDevices(), controllerOptions));
        if (!hasLocalPolicy(spec.getPlacementPolicy())) {
            errs.add("Only local placement is allowed for local services");
        }
        return errs.isEmpty()
               ? ValidationResult.success()
               : ValidationResult.failure(errs);
    }

    @Override
    @MonitoredFunction
    public ValidationResult validateOperation(
            DeployableLifeCycleManagementEngine<LocalServiceInfo, LocalServiceSpec, LocalServiceOperation, LocalServiceState,
                    LocalServiceActionContext, Action<LocalServiceInfo, LocalServiceState, LocalServiceActionContext,
                    LocalServiceOperation>> engine,
            LocalServiceOperation operation) {

        val serviceId = deployableObjectId(operation);
        if (Strings.isNullOrEmpty(serviceId)) {
            return ValidationResult.failure("No local service id found in operation");
        }
        if (!operation.getType().equals(CREATE)) {
            val currState = engine.currentState(serviceId).orElse(null);
            if (null == currState) {
                return ValidationResult.failure("No state found for local service: " + serviceId);
            }
            val allowedOpTypes = VALID_OPS_TABLE.getOrDefault(currState, Set.of());
            if (!allowedOpTypes.contains(operation.getType())) {
                if (allowedOpTypes.isEmpty()) {
                    return ValidationResult.failure(
                            "No operations allowed for " + serviceId + " as it is in " + currState.name() + " state");
                }
                else {
                    return ValidationResult.failure(
                            "Only " + allowedOpTypes.stream().sorted(Comparator.comparing(Enum::name)).toList()
                                    + " allowed for local service " + serviceId + " as it is in " + currState + " " +
                                    "state");
                }
            }
        }
        return operation.accept(new OpValidationVisitor(serviceId, localServiceStateDB, this, engine));
    }

    @RequiredArgsConstructor
    private static final class OpValidationVisitor implements LocalServiceOperationVisitor<ValidationResult> {

        private final String serviceId;
        private final LocalServiceStateDB localServiceStateDB;
        private final LocalServiceCommandValidator validator;

        private final DeployableLifeCycleManagementEngine<LocalServiceInfo, LocalServiceSpec, LocalServiceOperation, LocalServiceState,
                LocalServiceActionContext, Action<LocalServiceInfo, LocalServiceState, LocalServiceActionContext,
                LocalServiceOperation>> engine;

        @Override
        public ValidationResult visit(LocalServiceCreateOperation createOperation) {
            if (engine.exists(serviceId)) {
                return ValidationResult.failure("Local service " + serviceId + " already exists");
            }
            return validator.validateSpec(createOperation.getSpec());
        }

        @Override
        public ValidationResult visit(LocalServiceAdjustInstancesOperation localServiceAdjustInstancesOperation) {
            return ValidationResult.success();
        }

        @Override
        public ValidationResult visit(LocalServiceDeactivateOperation localServiceDeactivateOperation) {
            return ValidationResult.success();
        }

        @Override
        public ValidationResult visit(LocalServiceRestartOperation localServiceRestartOperation) {
            return ValidationResult.success();
        }

        @Override
        public ValidationResult visit(LocalServiceUpdateInstanceCountOperation localServiceUpdateInstanceCountOperation) {
            return localServiceStateDB.service(localServiceUpdateInstanceCountOperation.getServiceId())
                    .filter(service -> {
                        val policy = (LocalPlacementPolicy) service.getSpec().getPlacementPolicy();
                        return !policy.isHostLevel();
                    })
                    .map(s -> ValidationResult.success())
                    .orElse(ValidationResult.failure(
                            "Update is allowed for services that do not have Host Level option set"));
        }

        @Override
        public ValidationResult visit(LocalServiceDestroyOperation localServiceDestroyOperation) {
            return ValidationResult.success();
        }

        @Override
        public ValidationResult visit(LocalServiceActivateOperation localServiceActivateOperation) {
            return ValidationResult.success();
        }

        @Override
        public ValidationResult visit(LocalServiceReplaceInstancesOperation replaceInstancesOperation) {
            val unknownInstances = filterInvalidIds(replaceInstancesOperation.getInstanceIds());
            if (!unknownInstances.isEmpty()) {
                return ValidationResult.failure("There are no replaceable healthy instances with ids: " + unknownInstances);
            }
            return ValidationResult.success();
        }

        @Override
        public ValidationResult visit(LocalServiceStopInstancesOperation stopInstancesOperation) {
            val unknownInstances = filterInvalidIds(stopInstancesOperation.getInstanceIds());
            if (!unknownInstances.isEmpty()) {
                return ValidationResult.failure("There are no healthy instances with ids: " + unknownInstances);
            }
            return ValidationResult.success();

        }

        @Override
        public ValidationResult visit(LocalServiceDeployTestInstanceOperation localServiceDeployTestInstanceOperation) {
            return ValidationResult.success();
        }

        private List<String> filterInvalidIds(Collection<String> instances) {
            return Objects.requireNonNullElse(instances, List.<String>of())
                    .stream()
                    .filter(instanceId -> localServiceStateDB.instance(serviceId, instanceId)
                            .filter(instance -> instance.getState().equals(HEALTHY))
                            .isEmpty())
                    .toList();
        }
    }


}
