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
import java.util.List;

/**
 *
 */
@Value
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Jacksonized
public class ApplicationStopInstancesOperation extends ApplicationOperation {
    String appId;
    List<String> instanceIds;

    @NotNull
    @Valid
    ClusterOpSpec opSpec;

    public ApplicationStopInstancesOperation(
            String appId,
            List<String> instanceIds,
            ClusterOpSpec opSpec) {
        super(ApplicationOperationType.STOP_INSTANCES);
        this.appId = appId;
        this.instanceIds = instanceIds;
        this.opSpec = opSpec;
    }

    @Override
    public <T> T accept(ApplicationOperationVisitor<T> visitor) {
        return visitor.visit(this);
    }
}
