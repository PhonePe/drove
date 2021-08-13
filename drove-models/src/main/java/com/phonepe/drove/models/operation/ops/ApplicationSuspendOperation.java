package com.phonepe.drove.models.operation.ops;

import com.phonepe.drove.models.application.AppId;
import com.phonepe.drove.models.operation.ApplicationOperation;
import com.phonepe.drove.models.operation.ApplicationOperationType;
import com.phonepe.drove.models.operation.ApplicationOperationVisitor;
import com.phonepe.drove.models.operation.ClusterOpSpec;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.Value;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;

/**
 *
 */
@Value
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class ApplicationSuspendOperation extends ApplicationOperation {
    @NotNull
    @Valid
    AppId app;

    @NotNull
    @Valid
    ClusterOpSpec opSpec;

    public ApplicationSuspendOperation(AppId app, ClusterOpSpec opSpec) {
        super(ApplicationOperationType.SUSPEND);
        this.app = app;
        this.opSpec = opSpec;
    }

    @Override
    public <T> T visit(ApplicationOperationVisitor<T> visitor) {
        return visitor.visit(this);
    }
}
