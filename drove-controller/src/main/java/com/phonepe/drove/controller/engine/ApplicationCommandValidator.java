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

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Sets;
import com.phonepe.drove.controller.config.ControllerOptions;
import com.phonepe.drove.controller.resourcemgmt.ClusterResourcesDB;
import com.phonepe.drove.controller.statedb.ApplicationInstanceInfoDB;
import com.phonepe.drove.controller.statedb.ApplicationStateDB;
import com.phonepe.drove.controller.statemachine.applications.AppActionContext;
import com.phonepe.drove.controller.utils.ControllerUtils;
import com.phonepe.drove.models.application.ApplicationInfo;
import com.phonepe.drove.models.application.ApplicationSpec;
import com.phonepe.drove.models.application.ApplicationState;
import com.phonepe.drove.models.application.PortSpec;
import com.phonepe.drove.models.application.requirements.ResourceRequirement;
import com.phonepe.drove.models.application.requirements.ResourceType;
import com.phonepe.drove.models.instance.InstanceInfo;
import com.phonepe.drove.models.operation.ApplicationOperation;
import com.phonepe.drove.models.operation.ApplicationOperationType;
import com.phonepe.drove.models.operation.ApplicationOperationVisitor;
import com.phonepe.drove.models.operation.ops.*;
import com.phonepe.drove.statemachine.Action;
import io.appform.functionmetrics.MonitoredFunction;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.apache.commons.lang3.StringUtils;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.phonepe.drove.controller.utils.ControllerUtils.*;
import static com.phonepe.drove.models.application.ApplicationState.*;
import static com.phonepe.drove.models.instance.InstanceState.*;
import static com.phonepe.drove.models.operation.ApplicationOperationType.*;

/**
 *
 */
@Singleton
@Slf4j
@RequiredArgsConstructor(onConstructor_ = {@Inject})
public class ApplicationCommandValidator implements CommandValidator<ApplicationOperation, ApplicationSpec, DeployableLifeCycleManagementEngine<ApplicationInfo, ApplicationSpec, ApplicationOperation,
        ApplicationState, AppActionContext, Action<ApplicationInfo, ApplicationState, AppActionContext,
        ApplicationOperation>>> {
    private static final Map<ApplicationState, Set<ApplicationOperationType>> VALID_OPS_TABLE
            = ImmutableMap.<ApplicationState, Set<ApplicationOperationType>>builder()
            .put(INIT, Set.of())
            .put(MONITORING, Set.of(START_INSTANCES, SCALE_INSTANCES, DESTROY, RECOVER))
            .put(RUNNING, Set.of(START_INSTANCES, STOP_INSTANCES, SCALE_INSTANCES, REPLACE_INSTANCES, SUSPEND, RECOVER))
            .put(OUTAGE_DETECTED, Set.of())
            .put(SCALING_REQUESTED, Set.of(SCALE_INSTANCES))
            .put(STOP_INSTANCES_REQUESTED, Set.of())
            .put(REPLACE_INSTANCES_REQUESTED, Set.of())
            .put(DESTROY_REQUESTED, Set.of())
            .put(DESTROYED, Set.of())
            .put(FAILED, Set.of())
            .build();

    private final ApplicationStateDB applicationStateDB;
    private final ClusterResourcesDB clusterResourcesDB;
    private final ApplicationInstanceInfoDB instanceInfoStore;
    private final ControllerOptions controllerOptions;

    @Override
    public ValidationResult validateSpec(ApplicationSpec spec)  {
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
        if (null != spec.getExposureSpec() && !ports.containsKey(spec.getExposureSpec().getPortName())) {
            errs.add("Exposed port name " + spec.getExposureSpec().getPortName()
                             + " is undefined. Defined port names: " + ports.keySet());
        }
        errs.addAll(ensureWhitelistedVolumes(spec.getVolumes(), controllerOptions));
        errs.addAll(ensureCmdlArgs(spec.getArgs(), controllerOptions));
        errs.addAll(checkDeviceDisabled(spec.getDevices(), controllerOptions));
        if(hasLocalPolicy(spec.getPlacementPolicy())) {
            errs.add("Local service placement is not allowed for apps");
        }
        return errs.isEmpty()
               ? ValidationResult.success()
               : ValidationResult.failure(errs);
    }

    @Override
    @MonitoredFunction
    public ValidationResult validateOperation(final DeployableLifeCycleManagementEngine<ApplicationInfo, ApplicationSpec, ApplicationOperation,
            ApplicationState, AppActionContext, Action<ApplicationInfo, ApplicationState, AppActionContext,
            ApplicationOperation>> engine, final ApplicationOperation operation) {
        val appId = deployableObjectId(operation);
        if (Strings.isNullOrEmpty(appId)) {
            return ValidationResult.failure("no app id found in operation");
        }
        if (!operation.getType().equals(CREATE)) {
            val currState = engine.currentState(appId).orElse(null);
            if (null == currState) {
                return ValidationResult.failure("No state found for app: " + appId);
            }
            val allowedOpTypes = VALID_OPS_TABLE.getOrDefault(currState, Set.of());
            if (!allowedOpTypes.contains(operation.getType())) {
                if (allowedOpTypes.isEmpty()) {
                    return ValidationResult.failure(
                            "No operations allowed for " + appId + " as it is in " + currState.name() + " state");
                }
                else {
                    return ValidationResult.failure(
                            "Only " + allowedOpTypes.stream().sorted(Comparator.comparing(Enum::name)).toList()
                                    + " allowed for app " + appId + " as it is in " + currState + " state");
                }
            }
        }
        return operation.accept(new OpValidationVisitor(appId, applicationStateDB, clusterResourcesDB,
                                                        instanceInfoStore, this, engine));
    }

    @RequiredArgsConstructor
    private static final class OpValidationVisitor implements ApplicationOperationVisitor<ValidationResult> {

        private final String appId;
        private final ApplicationStateDB applicationStateDB;
        private final ClusterResourcesDB clusterResourcesDB;
        private final ApplicationInstanceInfoDB instancesDB;
        private final ApplicationCommandValidator validator;

        private final DeployableLifeCycleManagementEngine<ApplicationInfo, ApplicationSpec, ApplicationOperation,
                ApplicationState, AppActionContext, Action<ApplicationInfo, ApplicationState, AppActionContext,
                ApplicationOperation>> engine;

        @Override
        public ValidationResult visit(ApplicationCreateOperation create) {
            if (engine.exists(appId)) {
                return ValidationResult.failure("App " + appId + " already exists");
            }
            return validator.validateSpec(create.getSpec());
        }

        @Override
        public ValidationResult visit(ApplicationDestroyOperation destroy) {
            return ValidationResult.success();
        }

        @Override
        public ValidationResult visit(ApplicationStartInstancesOperation deploy) {
            val requiredInstances = deploy.getInstances();
            return ensureResources(requiredInstances);
        }

        @Override
        public ValidationResult visit(ApplicationStopInstancesOperation stopInstances) {
            val validIds = instancesDB.activeInstances(appId, 0, Integer.MAX_VALUE)
                    .stream()
                    .map(InstanceInfo::getInstanceId)
                    .collect(Collectors.toUnmodifiableSet());
            val invalidIds = Sets.difference(Set.copyOf(stopInstances.getInstanceIds()), validIds);
            return invalidIds.isEmpty()
                   ? ValidationResult.success()
                   : ValidationResult.failure(
                           "App " + appId + " does not have any instances with the following ids: "
                                   + StringUtils.join(invalidIds, ','));
        }

        @Override
        public ValidationResult visit(ApplicationScaleOperation scale) {
            val currentInstances = instancesDB.instanceCount(
                    appId, Set.of(PENDING, PROVISIONING, HEALTHY, STARTING, UNREADY, READY));
            val requiredInstances = scale.getRequiredInstances();
            if (requiredInstances <= currentInstances) {
                return ValidationResult.success();
            }
            return ensureResources(requiredInstances - currentInstances);
        }

        @Override
        public ValidationResult visit(ApplicationReplaceInstancesOperation replaceInstances) {
            val instancesToBeReplaced = replaceInstances.getInstanceIds();
            if (instancesToBeReplaced != null && !instancesToBeReplaced.isEmpty()) {
                val unknownInstances = instancesToBeReplaced.stream()
                        .filter(instanceId -> instancesDB.instance(appId, instanceId)
                                .filter(instance -> instance.getState().equals(HEALTHY))
                                .isEmpty())
                        .toList();
                if (!unknownInstances.isEmpty()) {
                    return ValidationResult.failure("There are no replaceable healthy instances with ids: " + unknownInstances);
                }
            }
            return ValidationResult.success();
        }

        @Override
        public ValidationResult visit(ApplicationSuspendOperation suspend) {
            return ValidationResult.success();
        }

        @Override
        public ValidationResult visit(ApplicationRecoverOperation recover) {
            return ValidationResult.success();
        }


        private ValidationResult ensureResources(long requiredNewInstances) {
            val executorCount = clusterResourcesDB.executorCount(true);
            if(executorCount == 0) {
                return ValidationResult.failure("No executors on cluster");
            }
            val spec = applicationStateDB.application(appId).map(ApplicationInfo::getSpec).orElse(null);
            if (null == spec) {
                return ValidationResult.failure("No spec found for app " + appId);
            }
            val errs = new ArrayList<String>();
            ControllerUtils.checkResources(clusterResourcesDB, spec, requiredNewInstances, errs);
            if (!errs.isEmpty()) {
                return ValidationResult.failure(errs);
            }
            return ValidationResult.success();
        }

    }


}
