package com.phonepe.drove.models.operation;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.phonepe.drove.models.operation.ops.*;
import lombok.Data;

/**
 *
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXISTING_PROPERTY, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(name = "CREATE", value = ApplicationCreateOperation.class),
        @JsonSubTypes.Type(name = "DESTROY", value = ApplicationDestroyOperation.class),
        @JsonSubTypes.Type(name = "DEPLOY", value = ApplicationDeployOperation.class),
        @JsonSubTypes.Type(name = "STOP_INSTANCES", value = ApplicationStopInstancesOperation.class),
        @JsonSubTypes.Type(name = "SCALE", value = ApplicationScaleOperation.class),
        @JsonSubTypes.Type(name = "RESTART", value = ApplicationRestartOperation.class),
        @JsonSubTypes.Type(name = "SUSPEND", value = ApplicationSuspendOperation.class),
        @JsonSubTypes.Type(name = "RECOVER", value = ApplicationRecoverOperation.class),
})
@Data
public abstract class ApplicationOperation {
    private final ApplicationOperationType type;

    public abstract <T> T accept(final ApplicationOperationVisitor<T> visitor);
}
