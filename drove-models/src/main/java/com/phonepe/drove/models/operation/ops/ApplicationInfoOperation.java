package com.phonepe.drove.models.operation.ops;

import com.phonepe.drove.models.application.AppId;
import com.phonepe.drove.models.operation.ApplicationOperation;
import com.phonepe.drove.models.operation.ApplicationOperationType;
import com.phonepe.drove.models.operation.ApplicationOperationVisitor;
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
public class ApplicationInfoOperation extends ApplicationOperation {
    @NotNull
    @Valid
    AppId app;

    public ApplicationInfoOperation(AppId app) {
        super(ApplicationOperationType.INFO);
        this.app = app;
    }

    @Override
    public <T> T visit(ApplicationOperationVisitor<T> visitor) {
        return visitor.visit(this);
    }
}
