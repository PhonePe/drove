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

import static com.phonepe.drove.controller.utils.ControllerUtils.checkMultipleGpuRequirements;
import static com.phonepe.drove.controller.utils.ControllerUtils.checkSecurityPolicy;
import static com.phonepe.drove.controller.utils.ControllerUtils.deployableObjectId;
import static com.phonepe.drove.controller.utils.ControllerUtils.hasLocalPolicy;
import static com.phonepe.drove.controller.utils.ControllerUtils.validateCheckSpec;
import static com.phonepe.drove.controller.utils.ControllerUtils.validateExposureSpec;
import static com.phonepe.drove.controller.utils.ControllerUtils.validatePlacementPolicyRules;
import static com.phonepe.drove.models.application.ApplicationState.DESTROYED;
import static com.phonepe.drove.models.application.ApplicationState.DESTROY_REQUESTED;
import static com.phonepe.drove.models.application.ApplicationState.FAILED;
import static com.phonepe.drove.models.application.ApplicationState.INIT;
import static com.phonepe.drove.models.application.ApplicationState.MONITORING;
import static com.phonepe.drove.models.application.ApplicationState.OUTAGE_DETECTED;
import static com.phonepe.drove.models.application.ApplicationState.REPLACE_INSTANCES_REQUESTED;
import static com.phonepe.drove.models.application.ApplicationState.RUNNING;
import static com.phonepe.drove.models.application.ApplicationState.SCALING_REQUESTED;
import static com.phonepe.drove.models.application.ApplicationState.STOP_INSTANCES_REQUESTED;
import static com.phonepe.drove.models.instance.InstanceState.HEALTHY;
import static com.phonepe.drove.models.instance.InstanceState.PENDING;
import static com.phonepe.drove.models.instance.InstanceState.PROVISIONING;
import static com.phonepe.drove.models.instance.InstanceState.READY;
import static com.phonepe.drove.models.instance.InstanceState.STARTING;
import static com.phonepe.drove.models.instance.InstanceState.UNREADY;
import static com.phonepe.drove.models.operation.ApplicationOperationType.CREATE;
import static com.phonepe.drove.models.operation.ApplicationOperationType.DESTROY;
import static com.phonepe.drove.models.operation.ApplicationOperationType.RECOVER;
import static com.phonepe.drove.models.operation.ApplicationOperationType.REPLACE_INSTANCES;
import static com.phonepe.drove.models.operation.ApplicationOperationType.SCALE_INSTANCES;
import static com.phonepe.drove.models.operation.ApplicationOperationType.START_INSTANCES;
import static com.phonepe.drove.models.operation.ApplicationOperationType.STOP_INSTANCES;
import static com.phonepe.drove.models.operation.ApplicationOperationType.SUSPEND;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import javax.inject.Inject;
import javax.inject.Singleton;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Sets;
import com.phonepe.drove.controller.resourcemgmt.ClusterResourcesDB;
import com.phonepe.drove.controller.resourcemgmt.InstanceScheduler;
import com.phonepe.drove.controller.rule.PlacementRuleEvaluator;
import com.phonepe.drove.controller.securityprofile.SecurityPolicyEnforcer;
import com.phonepe.drove.controller.statedb.ApplicationInstanceInfoDB;
import com.phonepe.drove.controller.statedb.ApplicationStateDB;
import com.phonepe.drove.controller.statemachine.applications.AppActionContext;
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
import com.phonepe.drove.models.operation.ops.ApplicationCreateOperation;
import com.phonepe.drove.models.operation.ops.ApplicationDestroyOperation;
import com.phonepe.drove.models.operation.ops.ApplicationRecoverOperation;
import com.phonepe.drove.models.operation.ops.ApplicationReplaceInstancesOperation;
import com.phonepe.drove.models.operation.ops.ApplicationScaleOperation;
import com.phonepe.drove.models.operation.ops.ApplicationStartInstancesOperation;
import com.phonepe.drove.models.operation.ops.ApplicationStopInstancesOperation;
import com.phonepe.drove.models.operation.ops.ApplicationSuspendOperation;
import com.phonepe.drove.statemachine.Action;

import org.apache.commons.lang3.StringUtils;

import io.appform.functionmetrics.MonitoredFunction;
import lombok.RequiredArgsConstructor;
import lombok.val;
import lombok.extern.slf4j.Slf4j;

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
    private final SecurityPolicyEnforcer securityPolicyEnforcer;
    private final InstanceScheduler instanceScheduler;
    private final PlacementRuleEvaluator ruleEvaluator;

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
        checkMultipleGpuRequirements(spec.getResources())
            .ifPresent(errs::add);
        errs.addAll(checkSecurityPolicy(spec, securityPolicyEnforcer));
        errs.addAll(validatePlacementPolicyRules(spec.getPlacementPolicy(), ruleEvaluator));
        errs.addAll(validateExposureSpec(spec, ports));
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
                                                        instanceInfoStore, instanceScheduler, this, engine));
    }

    @RequiredArgsConstructor
    private static final class OpValidationVisitor implements ApplicationOperationVisitor<ValidationResult> {

        private final String appId;
        private final ApplicationStateDB applicationStateDB;
        private final ClusterResourcesDB clusterResourcesDB;
        private final ApplicationInstanceInfoDB instancesDB;
        private final InstanceScheduler instanceScheduler;
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
            val checkResult = instanceScheduler.checkPlacement(spec, (int) requiredNewInstances);
            if (!checkResult.fits()) {
                return ValidationResult.failure(checkResult.getErrors());
            }
            return ValidationResult.success();
        }

    }


}
