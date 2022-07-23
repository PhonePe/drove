package com.phonepe.drove.controller.engine;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Sets;
import com.phonepe.drove.controller.resourcemgmt.ClusterResourcesDB;
import com.phonepe.drove.controller.statedb.ApplicationStateDB;
import com.phonepe.drove.controller.statedb.ApplicationInstanceInfoDB;
import com.phonepe.drove.controller.utils.ControllerUtils;
import com.phonepe.drove.models.application.ApplicationInfo;
import com.phonepe.drove.models.application.ApplicationState;
import com.phonepe.drove.models.application.PortSpec;
import com.phonepe.drove.models.application.checks.CheckModeSpecVisitor;
import com.phonepe.drove.models.application.checks.CheckSpec;
import com.phonepe.drove.models.application.checks.CmdCheckModeSpec;
import com.phonepe.drove.models.application.checks.HTTPCheckModeSpec;
import com.phonepe.drove.models.application.requirements.*;
import com.phonepe.drove.models.instance.InstanceInfo;
import com.phonepe.drove.models.operation.ApplicationOperation;
import com.phonepe.drove.models.operation.ApplicationOperationType;
import com.phonepe.drove.models.operation.ApplicationOperationVisitor;
import com.phonepe.drove.models.operation.ops.*;
import io.appform.functionmetrics.MonitoredFunction;
import lombok.Value;
import lombok.val;
import org.apache.commons.lang3.StringUtils;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.phonepe.drove.controller.utils.ControllerUtils.deployableObjectId;
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

    public enum ValidationStatus {
        SUCCESS,
        FAILURE
    }

    @Value
    public static class ValidationResult {
        ValidationStatus status;
        List<String> messages;

        public static ValidationResult success() {
            return new ValidationResult(ValidationStatus.SUCCESS, List.of("Success"));
        }

        public static ValidationResult failure(final String message) {
            return failure(List.of(message));
        }

        public static ValidationResult failure(final List<String> messages) {
            Objects.requireNonNull(messages, "Validation failure message cannot be empty");
            return new ValidationResult(ValidationStatus.FAILURE, messages);
        }
    }

    private final ApplicationStateDB applicationStateDB;
    private final ClusterResourcesDB clusterResourcesDB;
    private final ApplicationInstanceInfoDB instanceInfoStore;

    @Inject
    public CommandValidator(
            ApplicationStateDB applicationStateDB,
            ClusterResourcesDB clusterResourcesDB,
            ApplicationInstanceInfoDB instanceInfoStore) {
        this.applicationStateDB = applicationStateDB;
        this.clusterResourcesDB = clusterResourcesDB;
        this.instanceInfoStore = instanceInfoStore;
    }

    @MonitoredFunction
    public ValidationResult validate(final ApplicationEngine engine, final ApplicationOperation operation) {
        val appId = deployableObjectId(operation);
        if (Strings.isNullOrEmpty(appId)) {
            return ValidationResult.failure("no app id found in operation");
        }
        if (!operation.getType().equals(CREATE)) {
            val currState = engine.applicationState(appId).orElse(null);
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
        return operation.accept(new OpValidationVisitor(appId, applicationStateDB, clusterResourcesDB,
                                                        instanceInfoStore, engine));
    }

    private static final class OpValidationVisitor implements ApplicationOperationVisitor<ValidationResult> {

        private final String appId;
        private final ApplicationStateDB applicationStateDB;
        private final ClusterResourcesDB clusterResourcesDB;
        private final ApplicationInstanceInfoDB instancesDB;

        private final ApplicationEngine engine;
        private OpValidationVisitor(
                String appId,
                ApplicationStateDB applicationStateDB,
                ClusterResourcesDB clusterResourcesDB,
                ApplicationInstanceInfoDB instancesDB, ApplicationEngine engine) {
            this.appId = appId;
            this.applicationStateDB = applicationStateDB;
            this.clusterResourcesDB = clusterResourcesDB;
            this.instancesDB = instancesDB;
            this.engine = engine;
        }

        @Override
        public ValidationResult visit(ApplicationCreateOperation create) {
            if (engine.exists(appId)) {
                return CommandValidator.ValidationResult.failure("App " + appId + " already exists");
            }
            val errs = new ArrayList<String>();
            val spec = create.getSpec();
            val ports = spec.getExposedPorts()
                    .stream()
                    .collect(Collectors.toMap(PortSpec::getName, Function.identity()));
            validateCheckSpec(spec.getHealthcheck(), ports).ifPresent(errs::add);
            validateCheckSpec(spec.getReadiness(), ports).ifPresent(errs::add);
            val reqs = spec.getResources().stream().collect(Collectors.groupingBy(ResourceRequirement::getType, Collectors.counting()));
            if(!reqs.containsKey(ResourceType.CPU)) {
                errs.add("Cpu requirements are mandatory");
            }
            else if(!reqs.containsKey(ResourceType.MEMORY)) {
                errs.add("Memory requirements are mandatory");
            }
            if(null != spec.getExposureSpec() && !ports.containsKey(spec.getExposureSpec().getPortName())) {
                errs.add("Exposed port name " + spec.getExposureSpec().getPortName() + " is undefined");
            }
            return errs.isEmpty()
                ? ValidationResult.success()
                : ValidationResult.failure(errs);
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

        private static Optional<String> validateCheckSpec(CheckSpec spec, Map<String, PortSpec> ports) {
            return spec.getMode()
                    .accept(new CheckModeSpecVisitor<Optional<String>>() {
                        @Override
                        public Optional<String> visit(HTTPCheckModeSpec httpCheck) {
                            return ports.containsKey(httpCheck.getPortName())
                                   ? Optional.empty()
                                   : Optional.of("Invalid port name for health check: " + httpCheck.getPortName());
                        }

                        @Override
                        public Optional<String> visit(CmdCheckModeSpec cmdCheck) {
                            return Optional.empty();
                        }
                    });
        }
    }


}
