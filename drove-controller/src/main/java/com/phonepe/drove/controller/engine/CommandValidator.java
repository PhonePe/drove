package com.phonepe.drove.controller.engine;

import com.google.common.base.Joiner;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Sets;
import com.phonepe.drove.controller.resourcemgmt.ClusterResourcesDB;
import com.phonepe.drove.controller.statedb.ApplicationStateDB;
import com.phonepe.drove.controller.utils.ControllerUtils;
import com.phonepe.drove.models.application.ApplicationInfo;
import com.phonepe.drove.models.application.ApplicationState;
import com.phonepe.drove.models.application.requirements.CPURequirement;
import com.phonepe.drove.models.application.requirements.MemoryRequirement;
import com.phonepe.drove.models.application.requirements.ResourceRequirementVisitor;
import com.phonepe.drove.models.instance.InstanceInfo;
import com.phonepe.drove.models.operation.ApplicationOperation;
import com.phonepe.drove.models.operation.ApplicationOperationType;
import com.phonepe.drove.models.operation.ApplicationOperationVisitor;
import com.phonepe.drove.models.operation.ops.*;
import lombok.Value;
import lombok.val;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import static com.phonepe.drove.controller.utils.ControllerUtils.appId;
import static com.phonepe.drove.models.application.ApplicationState.*;
import static com.phonepe.drove.models.instance.InstanceState.*;
import static com.phonepe.drove.models.operation.ApplicationOperationType.*;

/**
 *
 */
@Singleton
public class CommandValidator {
    private static final Map<ApplicationState, Set<ApplicationOperationType>> VALID_OPS_TABLE
            = ImmutableMap.<ApplicationState, Set<ApplicationOperationType>>builder()
            .put(INIT, Set.of())
            .put(MONITORING, Set.of(DEPLOY, SCALE, DESTROY, RECOVER))
            .put(RUNNING, Set.of(DEPLOY, STOP_INSTANCES, SCALE, RESTART, SUSPEND, RECOVER))
            .put(DEPLOYMENT_REQUESTED, Set.of())
            .put(OUTAGE_DETECTED, Set.of())
            .put(SUSPEND_REQUESTED, Set.of())
            .put(SCALING_REQUESTED, Set.of(SCALE))
            .put(STOP_INSTANCES_REQUESTED, Set.of())
            .put(RESTART_REQUESTED, Set.of())
            .put(DESTROY_REQUESTED, Set.of())
            .put(DESTROYED, Set.of())
            .put(FAILED, Set.of())
            .build();

    public enum ValidationStatus {
        SUCCESS,
        FAILURE
    }

    @Value
    public static class ValidationResult {
        ValidationStatus status;
        String message;

        public static ValidationResult success() {
            return new ValidationResult(ValidationStatus.SUCCESS, "Success");
        }

        public static ValidationResult failure(final String message) {
            Objects.requireNonNull(message, "Validation failure message cannot be empty");
            return new ValidationResult(ValidationStatus.FAILURE, message);
        }
    }

    private final Provider<ApplicationEngine> engine;
    private final ApplicationStateDB applicationStateDB;
    private final ClusterResourcesDB clusterResourcesDB;

    @Inject
    public CommandValidator(
            Provider<ApplicationEngine> engine,
            ApplicationStateDB applicationStateDB,
            ClusterResourcesDB clusterResourcesDB) {
        this.engine = engine;
        this.applicationStateDB = applicationStateDB;
        this.clusterResourcesDB = clusterResourcesDB;
    }

    public ValidationResult validate(final ApplicationOperation operation) {
        val appId = appId(operation);
        if (Strings.isNullOrEmpty(appId)) {
            return ValidationResult.failure("no app id found in operation");
        }
        if (!operation.getType().equals(CREATE)) {
            val currState = engine.get().applicationState(appId).orElse(null);
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
                            "Only " + allowedOpTypes + " allowed for app " + appId + " as it is in " + currState + " state");
                }
            }
        }
        return operation.accept(new OpValidationVisitor(appId, applicationStateDB, clusterResourcesDB));
    }

    private static final class OpValidationVisitor implements ApplicationOperationVisitor<ValidationResult> {

        private final String appId;
        private final ApplicationStateDB applicationStateDB;
        private final ClusterResourcesDB clusterResourcesDB;

        private OpValidationVisitor(
                String appId,
                ApplicationStateDB applicationStateDB,
                ClusterResourcesDB clusterResourcesDB) {
            this.appId = appId;
            this.applicationStateDB = applicationStateDB;
            this.clusterResourcesDB = clusterResourcesDB;
        }

        @Override
        public ValidationResult visit(ApplicationCreateOperation create) {
            return /*applicationStateDB.application(appId).isPresent()
                   ? ValidationResult.failure("App " + appId + " already exists")
                   : */ValidationResult.success();
        }

        @Override
        public ValidationResult visit(ApplicationUpdateOperation update) {
            return ValidationResult.failure("Not implemented");
        }

        @Override
        public ValidationResult visit(ApplicationInfoOperation info) {
            return ValidationResult.failure("Not implemented");
        }

        @Override
        public ValidationResult visit(ApplicationDestroyOperation destroy) {
            return ValidationResult.success();
        }

        @Override
        public ValidationResult visit(ApplicationDeployOperation deploy) {
            val requiredInstances = deploy.getInstances();
            return ensureResources(requiredInstances);
        }

        @Override
        public ValidationResult visit(ApplicationStopInstancesOperation stopInstances) {
            val validIds = applicationStateDB.instances(appId, 0, Integer.MAX_VALUE)
                    .stream()
                    .map(InstanceInfo::getInstanceId)
                    .collect(Collectors.toUnmodifiableSet());
            val invalidIds = Sets.difference(Set.copyOf(stopInstances.getInstanceIds()), validIds);
            return invalidIds.isEmpty()
                   ? ValidationResult.success()
                   : ValidationResult.failure(
                           "App " + appId + " does not have any instances with the following ids: "
                                   + Joiner.on(',').join(invalidIds));
        }

        @Override
        public ValidationResult visit(ApplicationScaleOperation scale) {
            val currentInstances = applicationStateDB.instanceCount(
                    appId, Set.of(PENDING, PROVISIONING, HEALTHY, STARTING, UNREADY, READY));
            val requiredInstances = scale.getRequiredInstances();
            if (requiredInstances <= currentInstances) {
                return ValidationResult.success();
            }
            return ensureResources(requiredInstances - currentInstances);
        }

        @Override
        public ValidationResult visit(ApplicationRestartOperation restart) {
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


        private ValidationResult ensureResources(long requiredInstances) {
            val executors = clusterResourcesDB.currentSnapshot();
            var freeCores = 0;
            var freeMemory = 0L;
            for (val exec : executors) {
                freeCores += ControllerUtils.freeCores(exec);
                freeMemory += ControllerUtils.freeMemory(exec);
            }
            val spec = applicationStateDB.application(appId).map(ApplicationInfo::getSpec).orElse(null);
            if (null == spec) {
                return ValidationResult.failure("No spec found for app " + appId);
            }
            val requiredCores = requiredInstances
                    * spec.getResources()
                    .stream()
                    .mapToInt(r -> r.accept(new ResourceRequirementVisitor<Integer>() {
                        @Override
                        public Integer visit(CPURequirement cpuRequirement) {
                            return (int) cpuRequirement.getCount();
                        }

                        @Override
                        public Integer visit(MemoryRequirement memoryRequirement) {
                            return 0;
                        }
                    }))
                    .sum();
            val requiredMem = requiredInstances
                    * spec.getResources()
                    .stream()
                    .mapToLong(r -> r.accept(new ResourceRequirementVisitor<Long>() {
                        @Override
                        public Long visit(CPURequirement cpuRequirement) {
                            return 0L;
                        }

                        @Override
                        public Long visit(MemoryRequirement memoryRequirement) {
                            return memoryRequirement.getSizeInMB();
                        }
                    }))
                    .sum();
            if (requiredCores > freeCores) {
                return ValidationResult.failure("Cluster does not have enough CPU. Required: " + requiredCores + " Available: " + freeCores);
            }
            if (requiredMem > freeMemory) {
                return ValidationResult.failure("Cluster does not have enough Memory. Required: " + requiredMem + " Available: " + freeMemory);
            }
            return ValidationResult.success();
        }
    }
}
