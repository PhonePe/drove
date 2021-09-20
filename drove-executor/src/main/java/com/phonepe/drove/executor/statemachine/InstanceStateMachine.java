package com.phonepe.drove.executor.statemachine;

import com.phonepe.drove.common.StateData;
import com.phonepe.drove.common.StateMachine;
import com.phonepe.drove.common.Transition;
import com.phonepe.drove.common.model.InstanceSpec;
import com.phonepe.drove.executor.InstanceActionFactory;
import com.phonepe.drove.executor.statemachine.actions.*;
import com.phonepe.drove.models.instance.InstanceInfo;
import com.phonepe.drove.models.instance.InstanceState;
import lombok.NonNull;

import java.util.List;

/**
 *
 */
public class InstanceStateMachine extends StateMachine<InstanceInfo, InstanceState, InstanceActionContext, InstanceAction> {
    private static final List<Transition<InstanceInfo, InstanceState, InstanceActionContext, InstanceAction>> transitions
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
                             InstanceState.STOPPED)
                     );

    public InstanceStateMachine(
            InstanceSpec instanceSpec,
            @NonNull StateData<InstanceState, InstanceInfo> initalState,
            InstanceActionFactory actionFactory) {
        super(initalState, new InstanceActionContext(instanceSpec), actionFactory, transitions);
    }
}
