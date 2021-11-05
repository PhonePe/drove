package com.phonepe.drove.models.operation.ops;

import com.phonepe.drove.models.operation.ApplicationOperation;
import com.phonepe.drove.models.operation.ApplicationOperationType;
import com.phonepe.drove.models.operation.ApplicationOperationVisitor;
import com.phonepe.drove.models.operation.ClusterOpSpec;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import java.util.Objects;

/**
 *
 */
@Value
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Jacksonized
public class ApplicationSuspendOperation extends ApplicationOperation {
    @NotNull
    @Valid
    String appId;

    @NotNull
    @Valid
    ClusterOpSpec opSpec;

    public ApplicationSuspendOperation(String appId, ClusterOpSpec opSpec) {
        super(ApplicationOperationType.SUSPEND);
        this.appId = appId;
        this.opSpec = Objects.requireNonNullElse(opSpec, ClusterOpSpec.DEFAULT);
    }

    @Override
    public <T> T accept(ApplicationOperationVisitor<T> visitor) {
        return visitor.visit(this);
    }
}
