package com.phonepe.drove.models.info.resources.allocation;

import com.google.common.base.Preconditions;
import com.phonepe.drove.models.application.requirements.ResourceType;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

import java.util.Set;
@Value
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Jacksonized
@Builder
public class GPUAllocation extends ResourceAllocation{
    Set<String> gpuIds;
    long localGpuCount;

    public GPUAllocation(Set<String> gpuIds, long localGpuCount) {
        super(ResourceType.GPU);
        Preconditions.checkArgument(gpuIds != null && localGpuCount<= gpuIds.size());
        Preconditions.checkArgument(localGpuCount >= 0);

        this.gpuIds = gpuIds;
        this.localGpuCount = localGpuCount;
    }

    @Override
    public <T> T accept(ResourceAllocationVisitor<T> visitor) {
        return visitor.visit(this);
    }
}
