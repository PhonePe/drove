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
import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;

/**
 *
 */
@Value
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Jacksonized
public class ApplicationDeployOperation extends ApplicationOperation {
    @NotEmpty
    String appId;

    @Min(0)
    @Max(1024)
    int instances;

    @NotNull
    @Valid
    ClusterOpSpec opSpec;

    public ApplicationDeployOperation(String appId, int instances, ClusterOpSpec opSpec) {
        super(ApplicationOperationType.DEPLOY);
        this.appId = appId;
        this.instances = instances;
        this.opSpec = opSpec;
    }

    @Override
    public <T> T accept(ApplicationOperationVisitor<T> visitor) {
        return visitor.visit(this);
    }
}
