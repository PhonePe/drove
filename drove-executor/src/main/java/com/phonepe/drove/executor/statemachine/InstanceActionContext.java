package com.phonepe.drove.executor.statemachine;

import com.github.dockerjava.api.DockerClient;
import com.phonepe.drove.common.model.DeploymentUnitSpec;
import com.phonepe.drove.statemachine.ActionContext;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

/**
 *
 */
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class InstanceActionContext<T extends DeploymentUnitSpec> extends ActionContext<Void> {
    private final String executorId;
    private final T instanceSpec;
    private final DockerClient client;
    private String dockerImageId;
    private String dockerInstanceId;

    public InstanceActionContext(
            String executorId,
            T instanceSpec,
            DockerClient client) {
        super();
        this.executorId = executorId;
        this.instanceSpec = instanceSpec;
        this.client = client;
    }
}
