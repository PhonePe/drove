package com.phonepe.drove.executor.statemachine.application;

import com.github.dockerjava.api.DockerClient;
import com.phonepe.drove.common.model.ApplicationInstanceSpec;
import com.phonepe.drove.executor.InstanceActionFactory;
import com.phonepe.drove.executor.model.ExecutorApplicationInstanceInfo;
import com.phonepe.drove.executor.statemachine.InstanceActionBase;
import com.phonepe.drove.executor.statemachine.InstanceActionContext;
import com.phonepe.drove.executor.statemachine.application.actions.*;
import com.phonepe.drove.models.instance.InstanceState;
import com.phonepe.drove.statemachine.StateData;
import com.phonepe.drove.statemachine.StateMachine;
import com.phonepe.drove.statemachine.Transition;
import lombok.NonNull;

import java.util.List;

import static com.phonepe.drove.models.instance.InstanceState.*;

/**
 *
 */
public class ApplicationInstanceStateMachine extends StateMachine<ExecutorApplicationInstanceInfo, Void, InstanceState, InstanceActionContext<ApplicationInstanceSpec>, InstanceActionBase<ExecutorApplicationInstanceInfo, InstanceState, ApplicationInstanceSpec>> {
    private static final List<Transition<ExecutorApplicationInstanceInfo, Void, InstanceState, InstanceActionContext<ApplicationInstanceSpec>, InstanceActionBase<ExecutorApplicationInstanceInfo, InstanceState, ApplicationInstanceSpec>>> transitions
            = List.of(
            new Transition<>(PENDING,
                             ApplicationInstanceSpecValidator.class,
                             PROVISIONING,
                             STOPPING),
            new Transition<>(PROVISIONING,
                             ApplicationExecutableFetchAction.class,
                             STARTING,
                             PROVISIONING_FAILED),
            new Transition<>(STARTING,
                             ApplicationInstanceRunAction.class,
                             UNREADY,
                             START_FAILED),
            new Transition<>(UNKNOWN,
                             ApplicationInstanceRecoveryAction.class,
                             UNREADY,
                             STOPPED),
            new Transition<>(UNREADY,
                             ApplicationInstanceReadinessCheckAction.class,
                             READY,
                             READINESS_CHECK_FAILED,
                             STOPPING),
            new Transition<>(READY,
                             ApplicationInstanceSingularHealthCheckAction.class,
                             HEALTHY,
                             STOPPING),
            new Transition<>(HEALTHY,
                             ApplicationInstanceHealthcheckAction.class,
                             UNHEALTHY,
                             STOPPING),
            new Transition<>(STOPPING,
                             ApplicationInstanceStopAction.class,
                             DEPROVISIONING),
            new Transition<>(UNHEALTHY,
                             ApplicationInstanceStopAction.class,
                             DEPROVISIONING),
            new Transition<>(PROVISIONING_FAILED,
                             ApplicationInstanceDestroyAction.class,
                             DEPROVISIONING),
            new Transition<>(START_FAILED,
                             ApplicationInstanceDestroyAction.class,
                             DEPROVISIONING),
            new Transition<>(DEPROVISIONING,
                             ApplicationExecutableCleanupAction.class,
                             STOPPED),
            new Transition<>(READINESS_CHECK_FAILED,
                             ApplicationInstanceStopAction.class,
                             DEPROVISIONING));

    public ApplicationInstanceStateMachine(
            String executorId,
            ApplicationInstanceSpec instanceSpec,
            @NonNull StateData<InstanceState, ExecutorApplicationInstanceInfo> initalState,
            InstanceActionFactory<ExecutorApplicationInstanceInfo, InstanceState, ApplicationInstanceSpec> actionFactory,
            DockerClient client) {
        super(initalState, new InstanceActionContext<>(executorId, instanceSpec, client), actionFactory, transitions);
    }
}
