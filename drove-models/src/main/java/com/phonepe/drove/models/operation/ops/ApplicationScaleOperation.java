package com.phonepe.drove.models.operation.ops;

import com.phonepe.drove.models.operation.ApplicationOperation;
import com.phonepe.drove.models.operation.ApplicationOperationType;
import com.phonepe.drove.models.operation.ApplicationOperationVisitor;
import com.phonepe.drove.models.operation.ClusterOpSpec;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

import javax.validation.Valid;
import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;

/**
 *
 */
@Value
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Jacksonized
@Builder
public class ApplicationScaleOperation extends ApplicationOperation {
    @NotNull
    @Valid
    String appId;

    @Min(0)
    @Max(2048)
    long requiredInstances;

    @NotNull
    @Valid
    ClusterOpSpec opSpec;

    public ApplicationScaleOperation(String appId, long requiredInstances, ClusterOpSpec opSpec) {
        super(ApplicationOperationType.SCALE_INSTANCES);
        this.appId = appId;
        this.requiredInstances = requiredInstances;
        this.opSpec = opSpec;
    }

    @Override
    public <T> T accept(ApplicationOperationVisitor<T> visitor) {
        return visitor.visit(this);
    }
}
