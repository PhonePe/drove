package com.phonepe.drove.executor.statemachine;

import com.github.dockerjava.api.DockerClient;
import com.phonepe.drove.common.model.InstanceSpec;
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
public class InstanceActionContext extends ActionContext<Void> {
    private final String executorId;
    private final InstanceSpec instanceSpec;
    private final DockerClient client;
    private String dockerImageId;
    private String dockerInstanceId;

    public InstanceActionContext(
            String executorId,
            InstanceSpec instanceSpec,
            DockerClient client) {
        super();
        this.executorId = executorId;
        this.instanceSpec = instanceSpec;
        this.client = client;
    }
}
