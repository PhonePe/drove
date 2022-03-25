package com.phonepe.drove.executor.statemachine;

import com.github.dockerjava.api.DockerClient;
import com.phonepe.drove.common.StateData;
import com.phonepe.drove.common.StateMachine;
import com.phonepe.drove.common.Transition;
import com.phonepe.drove.common.model.InstanceSpec;
import com.phonepe.drove.executor.InstanceActionFactory;
import com.phonepe.drove.executor.model.ExecutorInstanceInfo;
import com.phonepe.drove.executor.statemachine.actions.*;
import com.phonepe.drove.models.instance.InstanceState;
import lombok.NonNull;

import java.util.List;

import static com.phonepe.drove.models.instance.InstanceState.*;

/**
 *
 */
public class InstanceStateMachine extends StateMachine<ExecutorInstanceInfo, Void, InstanceState, InstanceActionContext, InstanceAction> {
    private static final List<Transition<ExecutorInstanceInfo, Void, InstanceState, InstanceActionContext, InstanceAction>> transitions
            = List.of(
            new Transition<>(PENDING,
                             InstanceSpecValidator.class,
                             PROVISIONING),
            new Transition<>(PROVISIONING,
                             ExecutableFetchAction.class,
                             STARTING,
                             PROVISIONING_FAILED),
            new Transition<>(STARTING,
                             InstanceRunAction.class,
                             UNREADY,
                             START_FAILED),
            new Transition<>(UNKNOWN,
                             InstanceRecoveryAction.class,
                             UNREADY,
                             STOPPED),
            new Transition<>(UNREADY,
                             InstanceReadinessCheckAction.class,
                             READY,
                             READINESS_CHECK_FAILED,
                             STOPPING),
            new Transition<>(READY,
                             InstanceSingularHealthCheckAction.class,
                             HEALTHY,
                             STOPPING),
            new Transition<>(HEALTHY,
                             InstanceHealthcheckAction.class,
                             UNHEALTHY,
                             STOPPING),
            new Transition<>(STOPPING,
                             InstanceStopAction.class,
                             DEPROVISIONING),
            new Transition<>(UNHEALTHY,
                             InstanceStopAction.class,
                             DEPROVISIONING),
            new Transition<>(PROVISIONING_FAILED,
                             InstanceDestroyAction.class,
                             DEPROVISIONING),
            new Transition<>(START_FAILED,
                             InstanceDestroyAction.class,
                             DEPROVISIONING),
            new Transition<>(DEPROVISIONING,
                             ExecutableCleanupAction.class,
                             STOPPED),
            new Transition<>(READINESS_CHECK_FAILED,
                             InstanceStopAction.class,
                             DEPROVISIONING));

    public InstanceStateMachine(
            String executorId,
            InstanceSpec instanceSpec,
            @NonNull StateData<InstanceState, ExecutorInstanceInfo> initalState,
            InstanceActionFactory actionFactory, DockerClient client) {
        super(initalState, new InstanceActionContext(executorId, instanceSpec, client), actionFactory, transitions);
    }
}
