package com.phonepe.drove.executor;

import com.phonepe.drove.common.StateData;
import com.phonepe.drove.common.StateMachine;
import com.phonepe.drove.common.Transition;
import com.phonepe.drove.internalmodels.InstanceSpec;
import com.phonepe.drove.models.instance.InstanceInfo;
import com.phonepe.drove.models.instance.InstanceState;
import lombok.NonNull;

import java.util.List;

/**
 *
 */
public class InstanceStateMachine extends StateMachine<InstanceInfo, InstanceState, InstanceActionContext, InstanceAction> {
    private static final List<Transition<InstanceInfo, InstanceState, InstanceActionContext, InstanceAction>> TRANSITIONS
            = List.of(
            new Transition<>(InstanceState.PROVISIONING,
                             new DockerPullAction(),
                             InstanceState.STARTING,
                             InstanceState.PROVISIONING_FAILED),
            new Transition<>(InstanceState.STARTING,
                             new DockerRunAction(),
                             InstanceState.UNREADY,
                             InstanceState.START_FAILED),
            new Transition<>(InstanceState.UNREADY,
                             new InstanceReadinessCheckAction(),
                             InstanceState.READY,
                             InstanceState.READINESS_CHECK_FAILED,
                             InstanceState.STOPPING),
            new Transition<>(InstanceState.READY,
                             new InstanceSingularHealthCheckAction(),
                             InstanceState.HEALTHY,
                             InstanceState.STOPPING),
            new Transition<>(InstanceState.HEALTHY,
                             new InstanceHealthcheckAction(),
                             InstanceState.UNHEALTHY,
                             InstanceState.STOPPING),
            new Transition<>(InstanceState.STOPPING,
                             new DockerStopAction(),
                             InstanceState.DEPROVISIONING),
            new Transition<>(InstanceState.UNHEALTHY,
                             new DockerStopAction(),
                             InstanceState.DEPROVISIONING),
            new Transition<>(InstanceState.PROVISIONING_FAILED,
                             new InstanceDummyAction(InstanceState.DEPROVISIONING),
                             InstanceState.DEPROVISIONING),
            new Transition<>(InstanceState.START_FAILED,
                             new InstanceDummyAction(InstanceState.DEPROVISIONING),
                             InstanceState.DEPROVISIONING),
            new Transition<>(InstanceState.DEPROVISIONING,
                             new DockerCleanupAction(),
                             InstanceState.STOPPED)
            );

    public InstanceStateMachine(
            InstanceSpec instanceSpec,
            @NonNull StateData<InstanceState, InstanceInfo> initalState) {
        super(initalState, new InstanceActionContext(instanceSpec), TRANSITIONS);
    }
}
