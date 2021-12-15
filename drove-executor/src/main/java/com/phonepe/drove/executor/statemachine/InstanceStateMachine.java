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

/**
 *
 */
public class InstanceStateMachine extends StateMachine<ExecutorInstanceInfo, Void, InstanceState, InstanceActionContext, InstanceAction> {
    private static final List<Transition<ExecutorInstanceInfo, Void, InstanceState, InstanceActionContext, InstanceAction>> transitions
            = List.of(
            new Transition<>(InstanceState.PENDING,
                             InstanceSpecValidator.class,
                             InstanceState.PROVISIONING,
                             InstanceState.STOPPING),
            new Transition<>(InstanceState.PROVISIONING,
                             ExecutableFetchAction.class,
                             InstanceState.STARTING,
                             InstanceState.PROVISIONING_FAILED,
                             InstanceState.STOPPING),
            new Transition<>(InstanceState.STARTING,
                             InstanceRunAction.class,
                             InstanceState.UNREADY,
                             InstanceState.START_FAILED,
                             InstanceState.STOPPING),
            new Transition<>(InstanceState.UNKNOWN,
                             InstanceRecoveryAction.class,
                             InstanceState.UNREADY,
                             InstanceState.STOPPED),
            new Transition<>(InstanceState.UNREADY,
                             InstanceReadinessCheckAction.class,
                             InstanceState.READY,
                             InstanceState.READINESS_CHECK_FAILED,
                             InstanceState.STOPPING),
            new Transition<>(InstanceState.READY,
                             InstanceSingularHealthCheckAction.class,
                             InstanceState.HEALTHY,
                             InstanceState.STOPPING),
            new Transition<>(InstanceState.HEALTHY,
                             InstanceHealthcheckAction.class,
                             InstanceState.UNHEALTHY,
                             InstanceState.STOPPING),
            new Transition<>(InstanceState.STOPPING,
                             InstanceStopAction.class,
                             InstanceState.DEPROVISIONING),
            new Transition<>(InstanceState.UNHEALTHY,
                             InstanceStopAction.class,
                             InstanceState.DEPROVISIONING),
            new Transition<>(InstanceState.PROVISIONING_FAILED,
                             InstanceDestroyAction.class,
                             InstanceState.DEPROVISIONING),
            new Transition<>(InstanceState.START_FAILED,
                             InstanceDestroyAction.class,
                             InstanceState.DEPROVISIONING),
            new Transition<>(InstanceState.DEPROVISIONING,
                             ExecutableCleanupAction.class,
                             InstanceState.STOPPED),
            new Transition<>(InstanceState.READINESS_CHECK_FAILED,
                             InstanceStopAction.class,
                             InstanceState.DEPROVISIONING));

    public InstanceStateMachine(
            String executorId,
            InstanceSpec instanceSpec,
            @NonNull StateData<InstanceState, ExecutorInstanceInfo> initalState,
            InstanceActionFactory actionFactory, DockerClient client) {
        super(initalState, new InstanceActionContext(executorId, instanceSpec, client), actionFactory, transitions);
    }
}
