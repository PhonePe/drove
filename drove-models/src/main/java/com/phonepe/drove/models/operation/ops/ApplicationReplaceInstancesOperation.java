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
import java.util.Collections;
import java.util.Set;

/**
 *
 */
@Value
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Jacksonized
@Builder
public class ApplicationReplaceInstancesOperation extends ApplicationOperation {
    @NotEmpty
    String appId;

    Set<String> instanceIds;

    @NotNull
    @Valid
    ClusterOpSpec opSpec;

    public ApplicationReplaceInstancesOperation(String appId, Set<String> instanceIds, ClusterOpSpec opSpec) {
        super(ApplicationOperationType.REPLACE_INSTANCES);
        this.appId = appId;
        this.instanceIds = instanceIds == null ? Collections.emptySet() : instanceIds;
        this.opSpec = opSpec;
    }

    @Override
    public <T> T accept(ApplicationOperationVisitor<T> visitor) {
        return visitor.visit(this);
    }
}
