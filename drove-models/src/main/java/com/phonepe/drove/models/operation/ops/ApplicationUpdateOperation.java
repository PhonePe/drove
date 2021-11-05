package com.phonepe.drove.models.operation.ops;

import com.phonepe.drove.models.application.ApplicationSpec;
import com.phonepe.drove.models.operation.ApplicationOperation;
import com.phonepe.drove.models.operation.ApplicationOperationType;
import com.phonepe.drove.models.operation.ApplicationOperationVisitor;
import com.phonepe.drove.models.operation.ClusterOpSpec;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

import javax.validation.Valid;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;

/**
 *
 */
@Value
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Jacksonized
public class ApplicationUpdateOperation extends ApplicationOperation {
    @NotEmpty
    String appId;

    @NotNull
    @Valid
    ApplicationSpec spec;

    @NotNull
    @Valid
    ClusterOpSpec opeSpec;

    public ApplicationUpdateOperation(String appId, ApplicationSpec spec, ClusterOpSpec opeSpec) {
        super(ApplicationOperationType.UPDATE);
        this.appId = appId;
        this.spec = spec;
        this.opeSpec = opeSpec;
    }

    @Override
    public <T> T accept(ApplicationOperationVisitor<T> visitor) {
        return visitor.visit(this);
    }
}
