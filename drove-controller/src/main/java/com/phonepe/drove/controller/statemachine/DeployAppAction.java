package com.phonepe.drove.controller.statemachine;

import com.phonepe.drove.common.StateData;
import com.phonepe.drove.controller.resources.ClusterResourcesDB;
import com.phonepe.drove.controller.resources.InstanceScheduler;
import com.phonepe.drove.controller.statedb.ApplicationStateDB;
import com.phonepe.drove.models.application.ApplicationInfo;
import com.phonepe.drove.models.application.ApplicationState;
import com.phonepe.drove.models.instance.InstanceInfo;
import lombok.val;
import net.jodah.failsafe.RetryPolicy;

import javax.inject.Inject;
import java.time.Duration;
import java.util.List;

/**
 *
 */
public class DeployAppAction extends AppAction {
    private final InstanceScheduler scheduler;
    private final ApplicationStateDB applicationStateDB;
    private final ClusterResourcesDB clusterResourcesDB;

    @Inject
    public DeployAppAction(
            InstanceScheduler scheduler,
            ApplicationStateDB applicationStateDB,
            ClusterResourcesDB clusterResourcesDB) {
        this.scheduler = scheduler;
        this.applicationStateDB = applicationStateDB;
        this.clusterResourcesDB = clusterResourcesDB;
    }

    @Override
    public StateData<ApplicationState, ApplicationInfo> executeImpl(
            AppActionContext context, StateData<ApplicationState, ApplicationInfo> currentState) {
        val retryPolicy = new RetryPolicy<List<InstanceInfo>>()
                .withDelay(Duration.ofSeconds(3))
                .withMaxAttempts(50)
                .handle(Exception.class)
                .handleResultIf(instances -> instances.size() == context.getApplicationSpec().getInstances());
        return null;
    }

    @Override
    public void stop() {

    }
}
