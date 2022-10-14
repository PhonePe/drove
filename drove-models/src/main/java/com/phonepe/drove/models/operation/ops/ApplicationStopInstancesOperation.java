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
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import java.util.List;

/**
 *
 */
@Value
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Jacksonized
@Builder
public class ApplicationStopInstancesOperation extends ApplicationOperation {
    @NotEmpty
    String appId;

    @NotEmpty
    List<String> instanceIds;

    boolean skipRespawn;

    @NotNull
    @Valid
    ClusterOpSpec opSpec;

    public ApplicationStopInstancesOperation(
            String appId,
            List<String> instanceIds,
            boolean skipRespawn, ClusterOpSpec opSpec) {
        super(ApplicationOperationType.STOP_INSTANCES);
        this.appId = appId;
        this.instanceIds = instanceIds;
        this.skipRespawn = skipRespawn;
        this.opSpec = opSpec;
    }

    @Override
    public <T> T accept(ApplicationOperationVisitor<T> visitor) {
        return visitor.visit(this);
    }
}
